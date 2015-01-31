package viscel.narration

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import org.jsoup.nodes.Document
import org.scalactic.{Bad, ErrorMessage, Every, Good, One, Or, attempt}
import viscel.Log
import viscel.narration.SelectUtil._
import viscel.shared.Story.More.{Page, Unused}
import viscel.shared.{Story, ViscelUrl}

import scala.Predef.augmentString
import scala.annotation.tailrec
import scala.collection.immutable.Map
import scala.collection.JavaConverters.asScalaIteratorConverter

object Vid {

	case class Line(s: String, p: Int)
	type It = BufferedIterator[Line]

	val extractIDAndName = """^-(\w*):(.+)$""".r
	val extractAttribute = """^:(\w+)\s*(.*)$""".r

	def parseURL(it: It): ViscelUrl Or ErrorMessage = {
		val Line(url, pos) = it.next()
		attempt(SelectUtil.stringToVurl(url)).badMap(_ => s"malformed URL at line $pos: $url")
	}

	@tailrec
	def parseAttributes(it: It, acc: Map[String, Line]): Map[String, Line] =
		if (!it.hasNext) acc
		else it.head match {
			case Line(extractAttribute(name, value), pos) =>
				it.next()
				parseAttributes(it, acc.updated(name, Line(value, pos)))
			case _ => acc
		}

	implicit class ExtractContext (val sc : StringContext) {
		object extract {
			def unapplySeq[T](m: Map[String, T]): Option[Seq[T]] = {
				val keys = sc.parts.map(_.trim).filter(_.nonEmpty)
				val res = keys.flatMap(m.get)
				if (res.size == keys.size) Some(res)
				else None
			}
		}
	}

	def makeNarrator(id: String, name: String, pos: Int, url: ViscelUrl, attrs: Map[String, Line]): Narrator Or ErrorMessage = {
		val cid = "VD_" + (if (id.nonEmpty) id else name.replaceAll("\\s+", "").replaceAll("\\W", "_"))
		def has(keys: String*): Boolean = keys.forall(attrs.contains)

		val extractia = extract("ia")

		val pageFun: Option[Document => List[Story] Or Every[ErrorMessage]] = attrs match {
			case extract"ia $img" =>
				Some(doc => queryImageInAnchor(attrs("i").s, Page)(doc).badMap(_ :+ s"at line ${ img.p }"))

			case extract"i$img n$next" =>
				Some(doc => queryImageNext(img.s, next.s, Page)(doc).badMap(_ :+ s"at lines ${ img.p } or ${ next.p }"))

			case extract"i $img" =>
				Some(doc => queryImage(img.s)(doc).badMap(_ :+ s"at line ${ img.p }"))

			case _ => None
		}

		val archFun: Option[Document => List[Story] Or Every[ErrorMessage]] = attrs match {
			case extract"am $arch" =>
				Some(doc => SelectUtil.queryMixedArchive(arch.s, Page)(doc).badMap(_ :+ s"at line ${ arch.p }"))

			case _ => None
		}

		(pageFun, archFun) match {
			case (Some(pf), None) => Good(Templates.SF(cid, name, url, pf))
			case (Some(pf), Some(af)) => Good(Templates.AP(cid, name, url, af, pf))
			case _ => Bad(s"invalid combinations of attributes for $cid at line $pos")
		}

	}


	def parseNarration(it: It): Narrator Or ErrorMessage = {
		it.next() match {
			case Line(extractIDAndName(id, name), pos) =>
				parseURL(it).flatMap { url =>
					val attrs = parseAttributes(it, Map())
					makeNarrator(id, name, pos, url, attrs)
				}

			case Line(line, pos) => Bad(s"expected definition at line $pos, but found $line")
		}
	}

	def parse(lines: Iterator[String]): List[Narrator] Or ErrorMessage = {
		val preprocessed = lines.map(_.trim).zipWithIndex.map(p => Line(p._1, p._2)).filter(_.s.nonEmpty).buffered
		def go(it: It, acc: List[Narrator]): List[Narrator] Or ErrorMessage =
			if (!it.hasNext) {
				Good(acc)
			}
			else {
				parseNarration(it) match {
					case Good(n) => go(it, n :: acc)
					case Bad(e) => Bad(e)
				}
			}
		go(preprocessed, Nil)
	}

	def load(): List[Narrator] = {
		val dir = Paths.get("definitions")
		Files.createDirectories(dir)
		val paths = Files.newDirectoryStream(dir, "*.vid")
		paths.iterator().asScala.flatMap { p =>
			Log.info(s"parsing definitions from $p")
			parse(Files.lines(p, StandardCharsets.UTF_8).iterator().asScala) match {
				case Good(res) => res
				case Bad(err) =>
					Log.warn(s"failed to parse 'test.vid' errors: $err")
					Nil
			}
		}.toList
	}

}
