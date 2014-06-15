package viscel.wrapper

import com.typesafe.scalalogging.slf4j.StrictLogging
import viscel.core._
import viscel.store.rel
import viscel.store.rel.payload

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.jsoup.nodes.{Document, Element}
import org.scalactic.Accumulation._
import org.scalactic.TypeCheckedTripleEquals._
import org.scalactic._

import scala.collection.JavaConversions._
import scala.language.implicitConversions

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
