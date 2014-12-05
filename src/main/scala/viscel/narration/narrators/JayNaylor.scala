package viscel.narration.narrators

import org.jsoup.nodes.Document
import viscel.narration.SelectUtil.{elementIntoPointer, imgIntoAsset, storyFromOr}
import viscel.narration.{Narrator, Selection}
import viscel.shared.Story.{Chapter, More}
import viscel.shared.{AbsUri, Story}


object JayNaylor {

	class Common(val id: String, val name: String, val archiveUri: AbsUri) extends Narrator {
		override def archive: List[Story] = More(archiveUri, "archive") :: Nil

		def wrap(doc: Document, kind: String): List[Story] = storyFromOr(kind match {
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
