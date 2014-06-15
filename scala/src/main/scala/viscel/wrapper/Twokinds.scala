package viscel.wrapper

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.jsoup.nodes.{Document, Element}
import org.scalactic.Accumulation._
import org.scalactic.TypeCheckedTripleEquals._
import org.scalactic._
import viscel.core._

import scala.collection.JavaConversions._
import scala.language.implicitConversions

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
