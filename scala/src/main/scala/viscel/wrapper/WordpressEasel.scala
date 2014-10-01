package viscel.wrapper

import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import viscel.core.Core
import viscel.description.{Description, Pointer}
import viscel.wrapper.Util._

import scala.collection.immutable.Set


object WordpressEasel {

	case class Generic(id: String, name: String, start: String) extends Core {
		override def archive: List[Description] = Pointer(start, "") :: Nil
		override def wrap(doc: Document, pd: Pointer): List[Description] = Description.fromOr {
			val next_? = Selection(doc).optional("a.navi.navi-next").wrap(selectNext(""))
			val img_? = Selection(doc).unique("#comic img").wrapEach(imgIntoAsset)
			withGood(img_?, next_?) { _ ::: _ }
		}
	}

	val cores: Set[Core] = Set(
		Generic("ZombiesAndFairytales", "Zombies and Fairytales", "http://166612.webhosting66.1blu.de/zaf_de/wordpress/comic/erster-eindruck/")
	)
}
