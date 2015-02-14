package viscel.narration.narrators

import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import viscel.compat.v1.Story
import viscel.compat.v1.Story.More
import viscel.compat.v1.Story.More.{Kind, Unused}
import viscel.narration.SelectUtil._
import viscel.narration.{NarratorV1, Selection}

import scala.collection.immutable.Set


object WordpressEasel {

	case class Generic(id: String, name: String, start: String) extends NarratorV1 {
		override def archive: List[Story] = More(start, Unused) :: Nil
		override def wrap(doc: Document, kind: Kind): List[Story] = storyFromOr {
			val next_? = Selection(doc).optional("a.navi.navi-next").wrap(selectNext(Unused))
			val img_? = Selection(doc).unique("#comic img").wrapEach(imgIntoAsset)
			withGood(img_?, next_?) { _ ::: _ }
		}
	}

	val cores: Set[NarratorV1] = Set(
		Generic("ZombiesAndFairytales", "Zombies and Fairytales", "http://166612.webhosting66.1blu.de/zaf_de/wordpress/comic/erster-eindruck/")
	)
}
