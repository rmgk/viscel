package viscel.cores.concrete

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import org.scalactic._
import viscel.cores.Util._
import viscel.cores.{Core, Selection}
import viscel.description._


object Twokinds extends Core with StrictLogging {

	def archive = Pointer("http://twokinds.keenspot.com/?p=archive", "archive") :: Pointer("http://twokinds.keenspot.com/index.php", "main") :: Nil

	def id: String = "NX_Twokinds"

	def name: String = "Twokinds"

	def wrapArchive(doc: Document, pd: Pointer): List[Description] Or Every[ErrorMessage] = {
		val chapters_? = Selection(doc).many(".archive .chapter").wrapEach { chapter =>
			val title_? = Selection(chapter).unique("h4").getOne.map(_.ownText())
			val links_? = Selection(chapter).many("a").wrapEach { elementIntoPointer("page") }
			withGood(title_?, links_?) { (title, links) =>
				Chapter(title) :: links
			}
		}
		chapters_?.map(_.flatten(Predef.conforms))
	}

	def wrap(doc: Document, pd: Pointer): List[Description] = Description.fromOr(pd.pagetype match {
		case "archive" => wrapArchive(doc, pd)
		case "page" => Selection(doc).unique("#cg_img img").wrapEach { imgIntoAsset }
		case "main" => Selection(doc).unique(".comic img").wrapEach { imgIntoAsset }
	})
}
