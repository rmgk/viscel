package viscel.narration.narrators

import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import org.scalactic.{Or, Every, ErrorMessage}
import viscel.Log
import viscel.narration.SelectUtil.{elementIntoPointer, storyFromOr, queryImage}
import viscel.narration.{Selection, Narrator}
import viscel.shared.{AbsUri, Story}
import viscel.shared.Story.{Chapter, More}

import scala.collection.immutable.Set


object Funish {
	case class AP(override val id: String,
								override val name: String,
								start: AbsUri,
								wrapArchive: Document => List[Story] Or Every[ErrorMessage],
								wrapPage: Document => List[Story] Or Every[ErrorMessage]) extends Narrator {
		override def archive: List[Story] = More(start, "archive") :: Nil
		override def wrap(doc: Document, kind: String): List[Story] = storyFromOr(kind match {
			case "archive" => wrapArchive(doc)
			case "page" => wrapPage(doc)
		})
	}

	def cores: Set[Narrator] = Set(
		AP("NX_Fragile", "Fragile", "http://www.fragilestory.com/archive",
			doc => Selection(doc).unique("#content_inner_pages").many(".c_arch:has(div.a_2)").wrapFlat { chap =>
				val chapter_? = Selection(chap).first("div.a_2 > p").getOne.map(e => Chapter(e.text()))
				val pages_? = Selection(chap).many("a").wrapEach(elementIntoPointer("page"))
				withGood(chapter_?, pages_?)(_ :: _)
			},
			queryImage(_, "#content_comics > a > img"))
	)
}