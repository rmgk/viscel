package viscel.wrapper

import org.jsoup.nodes.Document
import viscel.core.{AbsUri, Core}
import viscel.description._
import viscel.wrapper.Util._

import scala.collection.immutable.Set

import scala.Predef.any2ArrowAssoc
import scala.collection.immutable.Map

object Batoto {

	case class Generic(id: String, name: String, archiveUri: AbsUri) extends Core {

		def archive = Pointer(archiveUri, "archive") :: Nil

		def wrap(doc: Document, pd: Pointer): List[Description] = Description.fromOr(pd.pagetype match {
			case "archive" =>
				Selection(doc).many(".lang_English.chapter_row > :first-child a").reverse.wrapFlat { elementIntoChapterPointer("front")	}
			case "page" =>
				Selection(doc).unique("#comic_page").wrapEach(imgIntoAsset)
			case "front" =>
				Selection(doc).first("#page_select").many("option").wrapEach { elementIntoPointer("page") }
		})
	}

	val cores: Set[Core] = Set(
		Generic("Batoto_mangaka-san-to-assistant-san-to-2", "Mangaka-san to Assistant-san to 2", "http://bato.to/comic/_/comics/mangaka-san-to-assistant-san-to-2-r9702")
	)

//	def getCore(desc: CoreDescription): Core = Generic(id = desc.id, name = desc.name, archiveUri = desc.metadata("start"))
//
//	object MetaCore extends Core {
//		override def id: String = "Meta_MangaHere"
//		override def name: String = "Metacore MangaHere"
//		override def archive: List[Description] = Pointer("http://www.mangahere.co/mangalist/", "") :: Nil
//		override def wrap(doc: Document, pd: Pointer): List[Description] = Description.fromOr(
//			Selection(doc).many("a.manga_info").wrapEach { anchor =>
//				val name = anchor.attr("rel")
//				val uri_? = extractUri(anchor)
//				val id_? = uri_?.flatMap { uri => Predef.wrapString("""manga/(\w+)/""").r.findFirstMatchIn(uri.toString)
//					.fold(Bad(One("match error")): String Or One[ErrorMessage])(m => Good(m.group(1)))
//				}
//				withGood(uri_?, id_?) { (uri, id) =>
//					CoreDescription("MangaHere", s"MangaHere_$id", name, Map("start" -> uri.toString))
//				}
//			})
//	}

}
