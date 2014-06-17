package viscel.wrapper

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import viscel.core._
import viscel.description._
import viscel.wrapper.Util._

import scala.language.implicitConversions

object Misfile extends Core with StrictLogging {
	def archive = Pointer("http://www.misfile.com/archives.php?arc=1&displaymode=wide&", "archive")

	def id: String = "NX_Misfile"

	def name: String = "Misfile"

	def chapterFrom(name: String, first: AbsUri) = Structure(
		payload = Chapter(name),
		children = Pointer(first, "page") :: Nil)

	def wrapArchive(doc: Document) = {
		val chapters_? = Selection(doc).many("#comicbody a:matchesOwn(^Book #\\d+$)").wrapEach { anchor =>
			withGood(anchorIntoPointer("page")(anchor)) { pointer =>
				Chapter(anchor.ownText()) :: pointer
			}
		}
		// the list of chapters is also the first page, wrap this directly
		val firstPage_? = wrapPage(doc)

		withGood(firstPage_?, chapters_?) { (page, chapters) =>
			Chapter("Book #1") :: page :: chapters :: EmptyDescription
		}
	}

	def wrapPage(doc: Document) = {
		val elements_? = Selection(doc)
			.unique(".comiclist table.wide_gallery")
			.many("[id~=^comic_\\d+$] .picture a")
			.unique("img").wrapEach { img => imgToElement(img).map { elem =>
			Structure(elem.copy(source = elem.source.replace("/t", "/")))
		}
		}
		val next_? = Selection(doc).all("a.next").wrap { selectNext("page") }

		withGood(elements_?, next_?) { _ :: _ }
	}

	def wrap(doc: Document, pd: Pointer): Description = Description.fromOr(pd.pagetype match {
		case "archive" => wrapArchive(doc)
		case "page" => wrapPage(doc)
	})
}
