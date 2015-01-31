package viscel.narration

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import org.scalactic.{Bad, ErrorMessage, Every, Good, One, Or, attempt}
import viscel.Log
import viscel.narration.SelectUtil.{queryImageInAnchor, queryImageNext}
import viscel.shared.Story.More.Unused
import viscel.shared.ViscelUrl

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


	def makeNarrator(id: String, name: String, url: ViscelUrl, attrs: Map[String, Line]): Narrator Or ErrorMessage = {
		val cid = "VD_" + (if (id.nonEmpty) id else name.replaceAll("\\s+", "").replaceAll("\\W", "_"))
		if (attrs.contains("i")) {
			val img = attrs("i")
			if (attrs.contains("n")) {
				val next = attrs("n")
				Good(Templates.SF(cid, name, url,
					doc => queryImageNext(img.s, next.s, Unused)(doc)
						.badMap(_ :+ s"at lines ${ img.p } or ${ next.p }")))
			}
			else Good(Templates.SF(cid, name, url,
				doc => queryImageInAnchor(attrs("i").s, Unused)(doc)
					.badMap(_ :+ s"at line ${ img.p }")))
		}
		else Bad(s"$id is missing required attribute 'i'")
	}


	def parseNarration(it: It): Narrator Or ErrorMessage = {
		it.next() match {
			case Line(extractIDAndName(id, name), pos) =>
				parseURL(it).flatMap { url =>
					val attrs = parseAttributes(it, Map())
					makeNarrator(id, name, url, attrs)
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
