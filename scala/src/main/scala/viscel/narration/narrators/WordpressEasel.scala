package viscel.narration.narrators

import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import viscel.narration.Story.More
import viscel.narration.Util._
import viscel.narration.{Narrator, Selection, Story}

import scala.collection.immutable.Set


object WordpressEasel {

	case class Generic(id: String, name: String, start: String) extends Narrator {
		override def archive: List[Story] = More(start, "") :: Nil
		override def wrap(doc: Document, pd: More): List[Story] = Story.fromOr {
			val next_? = Selection(doc).optional("a.navi.navi-next").wrap(selectNext(""))
			val img_? = Selection(doc).unique("#comic img").wrapEach(imgIntoAsset)
			withGood(img_?, next_?) { _ ::: _ }
		}
	}

	val cores: Set[Narrator] = Set(
		Generic("ZombiesAndFairytales", "Zombies and Fairytales", "http://166612.webhosting66.1blu.de/zaf_de/wordpress/comic/erster-eindruck/")
	)
}
