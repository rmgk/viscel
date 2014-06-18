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

		def archive = Pointer(archiveUri, "archive")

		def wrapArchive(doc: Document) = {
			Selection(doc).many(".detail_list > ul:first-of-type a").reverse.wrapEach { chapter =>
				val pointer_? = anchorIntoPointer("page")(chapter)
				withGood(pointer_?) { (pointer) =>
					Chapter(chapter.text()) :: pointer
				}
			}.map { chapters => Structure(children = chapters) }
		}

		def wrapPage(doc: Document) = {
			val next_? = Selection(doc).optional(".next_page:not([onclick])").wrap { selectNext("page") }
			val img_? = Selection(doc).unique("#image").wrapOne(imgIntoStructure)
			withGood(img_?, next_?) { _ :: _ }
		}

		def wrap(doc: Document, pd: Pointer): Description = Description.fromOr(pd.pagetype match {
			case "archive" => wrapArchive(doc)
			case "page" => wrapPage(doc)
		})
	}

	def getCore(node: CoreNode): Core = Generic(id = node.id, name = node.name, archiveUri = node[String]("start"))

	object MetaCore extends Core {
		override def id: String = "Meta_MangaHere"
		override def name: String = "Metacore MangaHere"
		override def archive: Description = Chapter("i should realy fix the need to have chapters everywhere") :: Pointer("http://www.mangahere.co/mangalist/", "")
		override def wrap(doc: Document, pd: Pointer): Description = Description.fromOr(
			Selection(doc).many("a.manga_info").wrapEach { anchor =>
				val name = anchor.attr("rel")
				val uri_? = extractUri(anchor)
				val id_? = uri_?.flatMap { uri => """manga/(\w+)/""".r.findFirstMatchIn(uri.toString)
					.fold(Bad(One("match error")): String Or One[ErrorMessage])(m => Good(m.group(1)))
				}
				withGood(uri_?, id_?) { (uri, id) =>
					CoreContent("MangaHere", s"MangaHere_$id", name, "start" -> uri.toString).toDescription
				}
			}.map(cores => Structure(children = cores)))
	}

}
