package viscel.wrapper

import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import org.scalactic._
import viscel.core.{AbsUri, Core}
import viscel.description._
import viscel.store.CoreNode
import viscel.wrapper.Util._

object MangaHere {

	case class Generic(id: String, name: String, archiveUri: AbsUri) extends Core {

		def archive = Pointer(archiveUri, "archive") :: Nil

		def wrapArchive(doc: Document): Or[List[Description], Every[ErrorMessage]] = {
			Selection(doc).many(".detail_list > ul:first-of-type a").reverse.wrapFlat { chapter =>
				val pointer_? = anchorIntoPointer("page")(chapter)
				withGood(pointer_?) { (pointer) =>
					Chapter(chapter.text()) :: pointer :: Nil
				}
			}
		}

		def wrapPage(doc: Document): Or[List[Description], Every[ErrorMessage]] = {
			val next_? = Selection(doc).optional(".next_page:not([onclick])").wrap { selectNext("page") }
			val img_? = Selection(doc).unique("#image").wrapEach(imgToAsset)
			withGood(img_?, next_?) { _ ::: _ }
		}

		def wrap(doc: Document, pd: Pointer): List[Description] = Description.fromOr(pd.pagetype match {
			case "archive" => wrapArchive(doc)
			case "page" => wrapPage(doc)
		})
	}

	def getCore(desc: CoreDescription): Core = Generic(id = desc.id, name = desc.name, archiveUri = desc.metadata("start"))

	object MetaCore extends Core {
		override def id: String = "Meta_MangaHere"
		override def name: String = "Metacore MangaHere"
		override def archive: List[Description] = Pointer("http://www.mangahere.co/mangalist/", "") :: Nil
		override def wrap(doc: Document, pd: Pointer): List[Description] = Description.fromOr(
			Selection(doc).many("a.manga_info").wrapEach { anchor =>
				val name = anchor.attr("rel")
				val uri_? = extractUri(anchor)
				val id_? = uri_?.flatMap { uri => """manga/(\w+)/""".r.findFirstMatchIn(uri.toString)
					.fold(Bad(One("match error")): String Or One[ErrorMessage])(m => Good(m.group(1)))
				}
				withGood(uri_?, id_?) { (uri, id) =>
					CoreDescription("MangaHere", s"MangaHere_$id", name, Map("start" -> uri.toString))
				}
			})
	}

}
