package viscel.wrapper

import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import org.scalactic._
import viscel.core.Core
import viscel.description._
import viscel.wrapper.Util._

import scala.Predef.any2ArrowAssoc
import scala.Predef.wrapString
import scala.collection.immutable.Map

object CloneManga {

	case class Clone(name: String, id: String, start: String) extends Core {
		override def archive = Pointer(start, "page") :: Nil
		override def wrap(doc: Document, pd: Pointer): List[Description] = Description.fromOr(Selection(doc).unique(".subsectionContainer").wrapOne { container =>
			val next_? = Selection(container).optional("> a:first-child").wrap(selectNext("page"))
			val img_? = Selection(container).unique("img").wrapOne(imgIntoAsset)
			withGood(img_?, next_?)(_ :: _)
		})
	}

	def getCore(desc: CoreDescription): Core = Clone(desc.name, desc.id, desc.metadata("start"))

	object MetaClone extends Core {
		override def id: String = "Meta_CloneManga"
		override def name: String = "Metacore Clonemanga"
		override def archive = Pointer("http://manga.clone-army.org/viewer_landing.php", "") :: Nil
		override def wrap(doc: Document, pd: Pointer): List[Description] = Description.fromOr(
			Selection(doc).many(".comicPreviewContainer").wrapEach { container =>
				val name_? = Selection(container).unique(".comicNote > h3").getOne.map(_.ownText())
				val uri_? = Selection(container).unique("> a").wrapOne(extractUri)
				val id_? = uri_?.flatMap { uri => """series=(\w+)""".r.findFirstMatchIn(uri.toString)
					.fold(Bad(One("match error")): String Or One[ErrorMessage])(m => Good(m.group(1)))
				}
				withGood(name_?, uri_?, id_?) { (name, uri, id) =>
					CoreDescription("CloneManga", s"CloneManga_$id", name, Map("start" -> s"$uri&start=1"))
				}
			})
	}

}
