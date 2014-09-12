package viscel.wrapper

import org.jsoup.nodes.Document
import viscel.core.{AbsUri, Core}
import viscel.description._
import viscel.wrapper.Util._
import org.scalactic.Accumulation._
import org.scalactic.Good

import scala.collection.immutable.Set

import scala.Predef.any2ArrowAssoc
import scala.collection.immutable.Map

object Batoto {

	case class Generic(id: String, name: String, first: AbsUri) extends Core {

		def archive = Pointer(first, "page") :: Nil

		def wrap(doc: Document, pd: Pointer): List[Description] = Description.fromOr {
			val nextElement = Selection(doc).unique("#comic_wrap a:has(#comic_page)")
			val next_? = nextElement.wrapOne(elementIntoPointer("page")).map { pointer =>
				if (pointer.loc.matches("http://bato.to/read/_/.*")) List(pointer) else List[Description]()
			}
			val img_? = nextElement.unique("#comic_page").wrapOne(imgIntoAsset)
			val chapter_? =
				if (doc.baseUri().matches(".*/\\d+$")) Good(Nil)
				else Selection(doc).first("select[name=chapter_select]").unique("option[selected=selected]").getOne.map(e => Chapter(e.text) :: Nil)
			withGood(next_?, img_?, chapter_?) {(next, img, chapter) => chapter ::: img :: next }
		}
	}

	val cores: Set[Core] = Set(
		Generic("Batoto_mangaka-san-to-assistant-san-to-2", "Mangaka-san to Assistant-san to 2", "http://bato.to/read/_/185677/mangaka-san-to-assistant-san-to-2_ch1_by_madman-scans"),
		Generic("Batoto_kimi-no-iru-machi", "Kimi no Iru Machi", "http://bato.to/read/_/46885/kimi-no-iru-machi_v1_ch1_by_red-hawk-scans"),
		Generic("Batoto_nisekoi", "Nisekoi", "http://bato.to/read/_/20464/nisekoi_by_cxc-scans")
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
