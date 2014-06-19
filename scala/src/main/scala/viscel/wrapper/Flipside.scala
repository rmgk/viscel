package viscel.wrapper

import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import viscel.core.Core
import viscel.description._
import viscel.wrapper.Util._

object Flipside extends Core {

	def archive = Pointer("http://flipside.keenspot.com/chapters.php", "archive") :: Nil

	def id: String = "NX_Flipside"

	def name: String = "Flipside"

	def wrapArchive(doc: Document) = {
		Selection(doc).many("td:matches(Chapter|Intermission)").wrapFlat { data =>
			val pages_? = Selection(data).many("a").wrapEach(anchorIntoPointer("page")).map{ _.distinct }
			val name_? = if (data.text.contains("Chapter"))
				Selection(data).unique("td:root > div:first-child").getOne.map{_.text()}
			else
				Selection(data).unique("p > b").getOne.map{_.text}

			withGood(pages_?, name_?) {(pages, name) =>
				Chapter(name) :: pages
			}
		}
	}

	def wrap(doc: Document, pd: Pointer): List[Description] = Description.fromOr(pd.pagetype match {
		case "archive" => wrapArchive(doc)
		case "page" => Selection(doc).unique("img.ksc").wrapEach(imgToAsset)
	})
}
