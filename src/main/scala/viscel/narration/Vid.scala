package viscel.narration

import java.io.{BufferedReader, InputStreamReader}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import org.jsoup.nodes.Document
import org.scalactic.{Bad, ErrorMessage, Every, Good, Or, attempt}
import viscel.narration.SelectUtil._
import viscel.shared.Story.Chapter
import viscel.shared.Story.More.Page
import viscel.shared.{Story, ViscelUrl}
import viscel.{Log, Viscel}

import scala.Predef.augmentString
import scala.annotation.tailrec
import scala.collection.JavaConverters.asScalaIteratorConverter
import scala.collection.immutable.Map

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


	implicit class ExtractContext(val sc: StringContext) {
		object extract {
			def unapplySeq[T](m: Map[String, T]): Option[Seq[T]] = {
				val keys = sc.parts.map(_.trim).filter(_.nonEmpty)
				val res = keys.flatMap(m.get)
				if (res.size == keys.size) Some(res)
				else None
			}
		}
	}

	def makeNarrator(id: String, name: String, pos: Int, startUrl: ViscelUrl, attrs: Map[String, Line]): Narrator Or ErrorMessage = {
		val cid = "VD_" + (if (id.nonEmpty) id else name.replaceAll("\\s+", "").replaceAll("\\W", "_"))
		type Wrap = Document => List[Story] Or Every[ErrorMessage]
		def has(keys: String*): Boolean = keys.forall(attrs.contains)
		def annotate(f: Wrap, lines: Line*): Option[Wrap] = Some(f.andThen(_.badMap(_ :+ s"at lines ${ lines.map(_.p) }")))
		def transform(ow: Option[Wrap])(f: List[Story] => List[Story]): Option[Wrap] = ow.map(_.andThen(_.map(f)))

		val pageFun: Option[Wrap] = attrs match {
			case extract"ia $img" => annotate(queryImageInAnchor(img.s, Page), img)

			case extract"i $img n $next" => annotate(queryImageNext(img.s, next.s, Page), img, next)

			case extract"is $img n $next" => annotate(doc => append(queryImages(img.s)(doc), queryNext(next.s, Page)(doc)), img, next)

			case extract"i $img" => annotate(queryImage(img.s), img)
			case extract"is $img" => annotate(queryImages(img.s), img)
			case _ => None
		}

		val archFun: Option[Wrap] = attrs match {
			case extract"am $arch" => annotate(queryMixedArchive(arch.s, Page), arch)

			case extract"ac $arch" => annotate(queryChapterArchive(arch.s, Page), arch)

			case _ => None
		}

		val (pageFunReplace, archFunReplace) = attrs match {
			case extract"url_replace $replacer" =>
				replacer.s.split("\\s+:::\\s+", 2) match {
					case Array(matches, replace) =>
						val doReplace: List[Story] => List[Story] = { stories =>
							stories.map {
								case Story.More(url, kind) => Story.More(url.replaceAll(matches, replace), kind)
								case o => o
							}
						}
						(transform(pageFun)(doReplace), transform(archFun)(doReplace))

					case _ => (None, None)
				}

			case _ => (pageFun, archFun)
		}

		val archFunRev = if (has("archiveReverse")) transform(archFunReplace) { stories =>
			groupedOn(stories) { case c@Chapter(_, _) => true; case _ => false }.reverse.flatMap {
				case (h :: t) => h :: t.reverse
				case Nil => Nil
			}
		}
		else archFunReplace

		(pageFunReplace, archFunRev) match {
			case (Some(pf), None) => Good(Templates.SF(cid, name, startUrl, pf))
			case (Some(pf), Some(af)) => Good(Templates.AP(cid, name, startUrl, af, pf))
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
		val preprocessed = lines.map(_.trim).zipWithIndex.map(p => Line(p._1, p._2 + 1)).filter(l => l.s.nonEmpty && !l.s.startsWith("--")).buffered
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

	def load(p: Path): List[Narrator] = {
		Log.info(s"parsing definitions from $p")
		parse(Files.lines(p, StandardCharsets.UTF_8).iterator().asScala) match {
			case Good(res) => res
			case Bad(err) =>
				Log.warn(s"failed to parse $p errors: $err")
				Nil
		}
	}

	def load(): List[Narrator] = {
		val dir = Viscel.basepath.resolve("definitions")
		val dynamic = if (!Files.exists(dir)) Nil
		else {
			val paths = Files.newDirectoryStream(dir, "*.vid")
			paths.iterator().asScala.flatMap { load }.toList
		}

		val stream = new BufferedReader(new InputStreamReader(getClass.getClassLoader.getResourceAsStream("definitions.vid"), StandardCharsets.UTF_8)).lines()
		val res = try {
			parse(stream.iterator().asScala) match {
				case Good(res) => res
				case Bad(err) =>
					Log.warn(s"failed to parse definitions.vid errors: $err")
					Nil
			}
		}
		finally stream.close()

		(res ::: dynamic).reverse
	}

}
