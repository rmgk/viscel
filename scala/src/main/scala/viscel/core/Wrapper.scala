package viscel.core

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.jsoup.nodes.{Document, Element}
import org.scalactic.Accumulation._
import org.scalactic.TypeCheckedTripleEquals._
import org.scalactic._

import scala.collection.JavaConversions._
import scala.language.implicitConversions

trait WrapperTools extends StrictLogging {

	def getAttr(e: Element, k: String): Option[(String, String)] = {
		val res = e.attr(k)
		if (res.isEmpty) None else Some(k -> res)
	}

	def imgToElement(img: Element): ElementContent = ElementContent(
		source = img.attr("abs:src"),
		origin = img.baseUri,
		props = (getAttr(img, "alt") ++
			getAttr(img, "title") ++
			getAttr(img, "width") ++
			getAttr(img, "height")).toMap)

	def caller(n: Int) = {
		val c = Thread.currentThread().getStackTrace()(n)
		s"${ c.getClassName }#${ c.getMethodName }:${ c.getLineNumber }"
	}

	def selectUnique(from: Element, query: String): Element Or One[ErrorMessage] = from.select(query) match {
		case rs if rs.size > 2 => Bad(One(s"query not unique ($query) at (${ caller(5) }) on (${ from.baseUri }, ${ from.tag }, #${ from.id }, .${ from.classNames })"))
		case rs if rs.size < 1 => Bad(One(s"query not found ($query) at (${ caller(5) }) on (${ from.baseUri }, ${ from.tag }, #${ from.id }, .${ from.classNames })"))
		case rs => Good(rs(0))
	}

	def selectSome(from: Element, query: String): Seq[Element] Or One[ErrorMessage] = from.select(query) match {
		case rs if rs.size < 1 => Bad(One(s"query did not match ($query) at (${ caller(5) }) on (${ from.baseUri }, ${ from.tag }, #${ from.id }, .${ from.classNames })"))
		case rs => Good(rs.toIndexedSeq)
	}

	def findParentTag(from: Element, tagname: String): Element Or One[ErrorMessage] =
		(from +: from.parents).find(_.tag.getName === tagname).fold(
			ifEmpty = Bad(One(s"$from has no parent $tagname")): Element Or One[ErrorMessage]
		)(Good(_))

	def chapter(name: String, children: Seq[Description] = Seq(), next: Description = EmptyDescription, props: Map[String, String] = Map()): StructureDescription =
		StructureDescription(
			payload = ChapterContent(name, props),
			children = children,
			next = next)
}

object Misfile extends Core with WrapperTools with StrictLogging {
	def archive = PointerDescription("http://www.misfile.com/archives.php?arc=1&displaymode=wide", "archive")

	def id: String = "NX_Misfile"

	def name: String = "Misfile"

	def chapterFrom(name: String, first: AbsUri) = StructureDescription(
		payload = ChapterContent(name),
		children = PointerDescription(first, "page") :: Nil)

	def wrapArchive(doc: Document, pd: PointerDescription): StructureDescription Or Every[ErrorMessage] = {
		val chapters = selectSome(doc, "#comicbody a:matchesOwn(^Book #\\d+$)").map {
			_.map { anchor =>
				chapterFrom(name = anchor.ownText, first = anchor.attr("abs:href"))
			}
		}
		withGood(wrapPage(doc, pd), chapters) { (page, chapters) =>
			StructureDescription(children =
				page.copy(payload = ChapterContent("Book #1"))
					+: chapters)
		}
	}

	def wrapPage(doc: Document, pd: PointerDescription): StructureDescription Or Every[ErrorMessage] = {
		selectUnique(doc, ".comiclist table.wide_gallery").flatMap { clist =>
			val elements_? = selectSome(clist, "[id~=^comic_\\d+$] .picture a").flatMap { anchors =>
				anchors.validatedBy { anchor =>
					selectUnique(anchor, "img").map { img =>
						StructureDescription(ElementContent(origin = anchor.attr("abs:href"), source = img.attr("abs:src").replace("/t", "/")))
					}
				}
			}
			val next = doc.select("a.next") match {
				case ns if ns.size > 0 => PointerDescription(ns(0).attr("abs:href"), "page")
				case ns => EmptyDescription
			}
			elements_?.map { elements => StructureDescription(children = elements, next = next) }
		}
	}

	def wrap(doc: Document, pd: PointerDescription): Description = Description.fromOr(pd.pagetype match {
		case "archive" => wrapArchive(doc, pd)
		case "page" => wrapPage(doc, pd)
	})
}

object Twokinds extends Core with WrapperTools with StrictLogging {
	def archive = StructureDescription(children =
		PointerDescription("http://twokinds.keenspot.com/?p=archive", "archive") ::
			PointerDescription("http://twokinds.keenspot.com/index.php", "main") :: Nil)

	def id: String = "NX_Twokinds"

	def name: String = "Twokinds"

	def chapterFrom(name: String, first: AbsUri) = StructureDescription(
		payload = ChapterContent(name),
		children = PointerDescription(first, "page") :: Nil)

	def wrapArchive(doc: Document, pd: PointerDescription): StructureDescription Or Every[ErrorMessage] = {
		selectSome(doc, ".archive .chapter").flatMap { chapters =>
			chapters.validatedBy { chapter =>
				val title_? = selectUnique(chapter, "h4").map(_.ownText())
				val links_? = selectSome(chapter, "a").map(_.map(_.attr("abs:href")))
				withGood(title_?, links_?) { (title, links) =>
					StructureDescription(payload = ChapterContent(title), children = links.map(PointerDescription(_, "page")))
				}
			}
		}.map { chapters => StructureDescription(children = chapters) }
	}

	def wrap(doc: Document, pd: PointerDescription): Description = Description.fromOr(pd.pagetype match {
		case "archive" => wrapArchive(doc, pd)
		case "page" => selectUnique(doc, "#cg_img img").map { img =>	StructureDescription(payload = imgToElement(img))	}
		case "main" => selectUnique(doc, ".comic img").map { img => StructureDescription(payload = imgToElement(img)) }
	})
}
