package viscel.wrapper

import org.jsoup.nodes.Document
import viscel.core.Core
import viscel.description._
import viscel.wrapper.Util._


object BetterDays extends Core {
	override def id: String = "NX_BetterDays"
	override def name: String = "Better Days"
	override def archive: List[Description] = Pointer("http://jaynaylor.com/betterdays/archives/chapter-1-honest-girls/", "archive") :: Nil

	def wrap(doc: Document, pd: Pointer): List[Description] = pd.pagetype match {
		case "archive" => Selection(doc).many("#chapters a").wrapFlat{ anchor =>
			val chap = Chapter(anchor.ownText())
			anchorIntoPointer("chapter")(anchor).map{List(chap, _)}
		}
		case "chapter" => Selection(doc).many("#comicentry .content img").wrapEach(imgToAsset)
	}
}
