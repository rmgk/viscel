package viscel.narration.narrators

import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import viscel.compat.v1.{NarratorV1, SelectUtilV1, SelectionV1, Story}
import viscel.compat.v1.Story.More
import viscel.compat.v1.Story.More.{Kind, Unused}
import SelectUtilV1._

import scala.collection.immutable.Set


object WordpressEasel {

	case class Generic(id: String, name: String, start: String) extends NarratorV1 {
		override def archive: List[Story] = More(start, Unused) :: Nil
		override def wrap(doc: Document, kind: Kind): List[Story] = storyFromOr {
			val next_? = SelectionV1(doc).optional("a.navi.navi-next").wrap(selectNext(Unused))
			val img_? = SelectionV1(doc).unique("#comic img").wrapEach(imgIntoAsset)
			withGood(img_?, next_?) { _ ::: _ }
		}
	}

	val cores: Set[NarratorV1] = Set(
		Generic("ZombiesAndFairytales", "Zombies and Fairytales", "http://166612.webhosting66.1blu.de/zaf_de/wordpress/comic/erster-eindruck/")
	)
}
