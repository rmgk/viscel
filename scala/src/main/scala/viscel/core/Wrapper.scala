package viscel.core

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.jsoup.nodes.{ Document, Element }
import org.scalactic.TypeCheckedTripleEquals._
import org.scalactic._
import org.scalactic.Accumulation._

import scala.collection.JavaConversions._
import scala.language.implicitConversions
import scala.util._

trait WrapperTools extends StrictLogging {

	def getAttr(e: Element, k: String): Option[(String, String)] = {
		val res = e.attr(k)
		if (res.isEmpty) None else Some(k -> res)
	}

	def imgToElement(img: Element): ElementDescription = ElementDescription(
		source = img.attr("abs:src"),
		origin = img.baseUri,
		props = (getAttr(img, "alt") ++
			getAttr(img, "title") ++
			getAttr(img, "width") ++
			getAttr(img, "height")).toMap)

	def selectUnique(from: Element, query: String): Element Or One[ErrorMessage] = from.select(query) match {
		case rs if rs.size > 2 => Bad(One(s"query not unique ($query) on (${from.baseUri}, ${from.tag}, #${from.id}, .${from.classNames})"))
		case rs if rs.size < 1 => Bad(One(s"query not found ($query) on (${from.baseUri}, ${from.tag}, #${from.id}, .${from.classNames})"))
		case rs => Good(rs(0))
	}

	def selectSome(from: Element, query: String): Seq[Element] Or One[ErrorMessage] = from.select(query) match {
		case rs if rs.size < 1 => Bad(One(s"query did not match ($query) on (${from.baseUri}, ${from.tag}, #${from.id}, .${from.classNames})"))
		case rs => Good(rs.toIndexedSeq)
	}

	def findParentTag(from: Element, tagname: String): Element Or One[ErrorMessage] =
		(from +: from.parents).find(_.tag.getName === tagname).fold(
			ifEmpty = Bad(One(s"$from has no parent $tagname")): Element Or One[ErrorMessage]
		)(Good(_))

	def chapter(name: String, children: Seq[Description] = Seq(), next: Description = EmptyDescription, props: Map[String, String] = Map()): StructureDescription =
		StructureDescription(
			payload = ChapterDescription(name, props),
			children = children,
			next = next)
}

object Misfile extends Core with WrapperTools with StrictLogging {
	def archive = PointerDescription("http://www.misfile.com/archives.php?arc=1&displaymode=wide", "archive")

	def id: String = "NX_Misfile"

	def name: String = "Misfile"

	def chapterFrom(name: String, first: AbsUri) = StructureDescription(
		payload = ChapterDescription(name),
		children = PointerDescription(first, "page") :: Nil)

	def wrapArchive(doc: Document, pd: PointerDescription): StructureDescription Or Every[ErrorMessage] = {
		val chapters = selectSome(doc, "#comicbody a:matchesOwn(^Book #\\d+$)").map {
			_.map { anchor =>
				chapterFrom(name = anchor.ownText, first = anchor.attr("abs:href"))
			}
		}
		withGood(wrapPage(doc, pd), chapters) { (page, chapters) =>
			StructureDescription(children =
				page.copy(payload = ChapterDescription("Book #1"))
					+: chapters)
		}
	}

	def wrapPage(doc: Document, pd: PointerDescription): StructureDescription Or Every[ErrorMessage] = {
		selectUnique(doc, ".comiclist table.wide_gallery").flatMap { clist =>
			val elements = selectSome(clist, "[id~=^comic_\\d+$] .picture a").map {
				_.map { anchor =>
					StructureDescription(ElementDescription(origin = anchor.attr("abs:href"), source = anchor.select("img").attr("abs:src").replace("/t", "/")))
				}
			}
			val next = doc.select("a.next") match {
				case ns if ns.size > 0 => PointerDescription(ns(0).attr("abs:href"), "page")
				case ns => EmptyDescription
			}
			elements.map { elem => StructureDescription(children = elem, next = next) }
		}
	}

	def wrap(doc: Document, pd: PointerDescription): Description = Description.fromOr(pd.pagetype match {
		case "archive" => wrapArchive(doc, pd)
		case "page" => wrapPage(doc, pd)
	})
}
