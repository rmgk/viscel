package viscel.narration.narrators

import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import org.scalactic._
import viscel.narration.Util._
import viscel.narration.{Story, Narrator, Selection}
import Story.More

import scala.Predef.{any2ArrowAssoc, augmentString}
import scala.collection.immutable.Map

object CloneManga {

	case class Clone(name: String, id: String, start: String) extends Narrator {
		override def archive = More(start, "page") :: Nil
		override def wrap(doc: Document, pd: More): List[Story] = Story.fromOr(Selection(doc).unique(".subsectionContainer").wrapOne { container =>
			val next_? = Selection(container).optional("> a:first-child").wrap(selectNext("page"))
			val img_? = Selection(container).unique("img").wrapOne(imgIntoAsset)
			withGood(img_?, next_?)(_ :: _)
		})
	}

	def getCore(desc: Story.Core): Narrator = Clone(desc.name, desc.id, desc.metadata("start"))

	object MetaClone extends Narrator {
		override def id: String = "Meta_CloneManga"
		override def name: String = "Metacore Clonemanga"
		override def archive = More("http://manga.clone-army.org/viewer_landing.php", "") :: Nil
		override def wrap(doc: Document, pd: More): List[Story] = Story.fromOr(
			Selection(doc).many(".comicPreviewContainer").wrapEach { container =>
				val name_? = Selection(container).unique(".comicNote > h3").getOne.map(_.ownText())
				val uri_? = Selection(container).unique("> a").wrapOne(extractUri)
				val id_? = uri_?.flatMap { uri => """series=(\w+)""".r.findFirstMatchIn(uri.toString)
					.fold(Bad(One("match error")): String Or One[ErrorMessage])(m => Good(m.group(1)))
				}
				withGood(name_?, uri_?, id_?) { (name, uri, id) =>
					Story.Core("CloneManga", s"CloneManga_$id", name, Map("start" -> s"$uri&start=1"))
				}
			})
	}

}
