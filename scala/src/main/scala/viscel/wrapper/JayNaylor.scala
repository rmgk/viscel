package viscel.wrapper

import org.jsoup.nodes.Document
import viscel.core.{AbsUri, Core}
import viscel.description._
import viscel.wrapper.Util._


object JayNaylor {
	
	class Common(val id: String, val name: String, val archiveUri: AbsUri) extends Core {
		override def archive: List[Description] = Pointer(archiveUri, "archive") :: Nil

		def wrap(doc: Document, pd: Pointer): List[Description] = pd.pagetype match {
			case "archive" => Selection(doc).many("#chapters li > a").wrapFlat { anchor =>
				val chap = Chapter(anchor.ownText())
				elementIntoPointer("chapter")(anchor).map { List(chap, _) }
			}
			case "chapter" => Selection(doc).many("#comicentry .content img").wrapEach(imgIntoAsset)
		}
	}
		
	object BetterDays extends Common("NX_BetterDays", "Better Days", "http://jaynaylor.com/betterdays/archives/chapter-1-honest-girls/")
	object OriginalLife extends Common("NX_OriginalLife", "Original Life", "http://jaynaylor.com/originallife/")
}
