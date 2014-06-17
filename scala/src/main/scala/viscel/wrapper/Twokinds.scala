package viscel.wrapper

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import org.scalactic._
import viscel.core._
import viscel.description._
import viscel.wrapper.Util._


object Twokinds extends Core with StrictLogging {

	def archive = Pointer("http://twokinds.keenspot.com/?p=archive", "archive") :: Pointer("http://twokinds.keenspot.com/index.php", "main")

	def id: String = "NX_Twokinds"

	def name: String = "Twokinds"

	def wrapArchive(doc: Document, pd: Pointer): Structure Or Every[ErrorMessage] = {
		val chapters_? = Selection(doc).many(".archive .chapter").wrapEach { chapter =>
			val title_? = Selection(chapter).unique("h4").getOne.map(_.ownText())
			val links_? = Selection(chapter).many("a").wrapEach { anchorIntoPointer("page") }
			withGood(title_?, links_?) { (title, links) =>
				Chapter(title) :: links :: EmptyDescription
			}
		}
		chapters_?.map(chapters => Structure(children = chapters))
	}

	def wrap(doc: Document, pd: Pointer): Description = Description.fromOr(pd.pagetype match {
		case "archive" => wrapArchive(doc, pd)
		case "page" => Selection(doc).unique("#cg_img img").wrapOne { imgIntoStructure }
		case "main" => Selection(doc).unique(".comic img").wrapOne { imgIntoStructure }
	})
}
