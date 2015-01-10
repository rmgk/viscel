package viscel.narration.narrators

import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import org.scalactic.{Every, Good, Or, Bad, One, ErrorMessage}
import viscel.narration.SelectUtil._
import viscel.narration.{Metarrator, Narrator, Selection}
import viscel.shared.{ViscelUrl, Story}
import viscel.shared.Story.More

import scala.Predef.ArrowAssoc
import scala.Predef.augmentString

object CloneManga {

	case class Clone(id: String, name: String, start: String) extends Narrator {
		override def archive = More(start, "page") :: Nil
		override def wrap(doc: Document, kind: String): List[Story] = storyFromOr(Selection(doc).unique(".subsectionContainer").wrapOne { container =>
			val next_? = Selection(container).optional("> a:first-child").wrap(selectNext("page"))
			val img_? = Selection(container).unique("img").wrapOne(imgIntoAsset)
			withGood(img_?, next_?)(_ :: _)
		})
	}


	object MetaClone extends Metarrator[Clone]("CloneManga") {
		override def archive: ViscelUrl = stringToVurl("http://manga.clone-army.org/viewer_landing.php")
		override def wrap(doc: Document): List[Clone] Or Every[ErrorMessage] =
			Selection(doc).many(".comicPreviewContainer").wrapEach { container =>
				val name_? = Selection(container).first(".comicNote > h3").getOne.map(_.ownText())
				val uri_? = Selection(container).unique("> a").wrapOne(extractUri)
				val id_? = uri_?.flatMap { uri => """series=(\w+)""".r.findFirstMatchIn(uri.toString)
					.fold(Bad(One("match error")): String Or One[ErrorMessage])(m => Good(m.group(1)))
				}
				withGood(name_?, uri_?, id_?) { (name, uri, id) =>
					Clone(s"CloneManga_$id", s"[CM] $name",  s"$uri&start=1")
				}
			}

	}


}
