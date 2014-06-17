package viscel.wrapper

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import org.scalactic._
import viscel.core._
import viscel.description.{Chapter, Structure, Pointer, Description}

object Twokinds extends Core with WrapperTools with StrictLogging {
	def archive = Structure(children =
		Pointer("http://twokinds.keenspot.com/?p=archive", "archive") ::
			Pointer("http://twokinds.keenspot.com/index.php", "main") :: Nil)

	def id: String = "NX_Twokinds"

	def name: String = "Twokinds"

	def wrapArchive(doc: Document, pd: Pointer): Structure Or Every[ErrorMessage] = {
		selectSome(doc, ".archive .chapter").flatMap { chapters =>
			chapters.validatedBy { chapter =>
				val title_? = selectUnique(chapter, "h4").map(_.ownText())
				val links_? = selectSome(chapter, "a").map(_.map(_.attr("abs:href")))
				withGood(title_?, links_?) { (title, links) =>
					Structure(payload = Chapter(title), children = links.map(Pointer(_, "page")))
				}
			}
		}.map { chapters => Structure(children = chapters) }
	}

	def wrap(doc: Document, pd: Pointer): Description = Description.fromOr(pd.pagetype match {
		case "archive" => wrapArchive(doc, pd)
		case "page" => selectUnique(doc, "#cg_img img").map { img => Structure(payload = imgToElement(img)) }
		case "main" => selectUnique(doc, ".comic img").map { img => Structure(payload = imgToElement(img)) }
	})
}
