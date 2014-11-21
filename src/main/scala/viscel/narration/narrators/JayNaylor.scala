package viscel.narration.narrators

import org.jsoup.nodes.Document
import viscel.crawler.AbsUri
import viscel.shared.Story
import Story.{Chapter, More}
import viscel.narration.Util.{elementIntoPointer, imgIntoAsset}
import viscel.narration.{Narrator, Selection}


object JayNaylor {

	class Common(val id: String, val name: String, val archiveUri: AbsUri) extends Narrator {
		override def archive: List[Story] = More(archiveUri, "archive") :: Nil

		def wrap(doc: Document, pd: More): List[Story] = Story.fromOr(pd.pagetype match {
			case "archive" => Selection(doc).many("#chapters li > a").wrapFlat { anchor =>
				val chap = Chapter(anchor.ownText())
				elementIntoPointer("chapter")(anchor).map { List(chap, _) }
			}
			case "chapter" => Selection(doc).many("#comicentry .content img").wrapEach(imgIntoAsset)
		})
	}

	object BetterDays extends Common("NX_BetterDays", "Better Days", "http://jaynaylor.com/betterdays/archives/chapter-1-honest-girls/")

	object OriginalLife extends Common("NX_OriginalLife", "Original Life", "http://jaynaylor.com/originallife/")

}
