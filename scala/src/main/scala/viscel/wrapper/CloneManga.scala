package viscel.wrapper

import org.jsoup.nodes.Document
import viscel.core.Core
import viscel.description._
import viscel.wrapper.Util._
import org.scalactic.Accumulation._


object CloneManga {

	case class Clone(name: String, idPart: String) extends Core {
		override def id: String = s"CloneManga_$idPart"
		override def archive: Description =  Chapter("") :: Pointer(s"http://manga.clone-army.org/viewer.php?series=$idPart&lang=english&page=1", "page")
		override def wrap(doc: Document, pd: Pointer): Description = Description.fromOr(Selection(doc).unique(".subsectionContainer").wrapOne{ container =>
			val next_? = Selection(container).optional("> a:first-child").wrap(selectNext("page"))
			val img_? = Selection(container).unique("img").wrapOne(imgIntoStructure)
			withGood( img_?, next_?) ( _ :: _ )
		})
	}

	val cores: Set[Core] = (
		Clone("April and May", "anm") ::
		Clone("Paper Eleven", "pxi") ::
		Clone("NNN", "nnn") ::
		Clone("Nana’s Everyday Life", "nana") ::
		Clone("June in Summer", "jis") ::
		Clone("HH", "hh") ::
		Clone("Kanami", "kanami") ::
		Clone("Penny Tribute", "penny") ::
		Clone("Momoka Korner", "penny") ::
		Nil).toSet
}
