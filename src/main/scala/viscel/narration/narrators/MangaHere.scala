package viscel.narration.narrators

import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import org.scalactic._
import viscel.narration.SelectUtil._
import viscel.narration.{Narrator, Selection}
import viscel.shared.{AbsUri, Story}

object MangaHere {

	case class Generic(id: String, name: String, archiveUri: AbsUri) extends Narrator {

		def archive = Story.More(archiveUri, "archive") :: Nil

		def wrapArchive(doc: Document): Or[List[Story], Every[ErrorMessage]] = {
			Selection(doc).many(".detail_list > ul:first-of-type a").reverse.wrapFlat { elementIntoChapterPointer("page") }
		}

		def wrapPage(doc: Document): Or[List[Story], Every[ErrorMessage]] = {
			val next_? = Selection(doc).optional(".next_page:not([onclick])").wrap { selectNext("page") }
			val img_? = Selection(doc).unique("#image").wrapEach(imgIntoAsset)
			withGood(img_?, next_?) { _ ::: _ }
		}

		def wrap(doc: Document, kind: String): List[Story] = storyFromOr(kind match {
			case "archive" => wrapArchive(doc)
			case "page" => wrapPage(doc)
		})
	}

	//	def getCore(desc: Story.Core): Narrator = Generic(id = desc.id, name = desc.name, archiveUri = desc.metadata("start"))
	//
	//	object MetaCore extends Narrator {
	//		override def id: String = "Meta_MangaHere"
	//		override def name: String = "Metacore MangaHere"
	//		override def archive: List[Story] = More("http://www.mangahere.co/mangalist/", "") :: Nil
	//		override def wrap(doc: Document, kind: String): List[Story] = storyFromOr(
	//			Selection(doc).many("a.manga_info").wrapEach { anchor =>
	//				val name = anchor.attr("rel")
	//				val uri_? = extractUri(anchor)
	//				val id_? = uri_?.flatMap { uri => Predef.wrapString( """manga/(\w+)/""").r.findFirstMatchIn(uri.toString)
	//					.fold(Bad(One("match error")): String Or One[ErrorMessage])(m => Good(m.group(1)))
	//				}
	//				withGood(uri_?, id_?) { (uri, id) =>
	//					Story.Core("MangaHere", s"MangaHere_$id", name, Map("start" -> uri.toString))
	//				}
	//			})
	//	}

}
