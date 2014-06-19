package viscel.wrapper

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import org.scalactic.{ErrorMessage, Every, Or}
import viscel.core._
import viscel.description._
import viscel.wrapper.Util._

import scala.language.implicitConversions

object Misfile extends Core with StrictLogging {
	def archive = Pointer("http://www.misfile.com/archives.php?arc=1&displaymode=wide&", "archive") :: Nil

	def id: String = "NX_Misfile"

	def name: String = "Misfile"

	def wrapArchive(doc: Document): Or[List[Description], Every[ErrorMessage]] = {
		val chapters_? = Selection(doc).many("#comicbody a:matchesOwn(^Book #\\d+$)").wrapFlat { anchor =>
			withGood(anchorIntoPointer("page")(anchor)) { pointer =>
				Chapter(anchor.ownText()) :: pointer :: Nil
			}
		}
		// the list of chapters is also the first page, wrap this directly
		val firstPage_? = wrapPage(doc)

		withGood(firstPage_?, chapters_?) { (page, chapters) =>
			Chapter("Book #1") :: page ::: chapters
		}
	}

	def wrapPage(doc: Document): Or[List[Description], Every[ErrorMessage]] = {
		val elements_? = Selection(doc)
			.unique(".comiclist table.wide_gallery")
			.many("[id~=^comic_\\d+$] .picture a").wrapEach { anchor =>
				val element_? = Selection(anchor).unique("img").wrapOne { imgToAsset }
				val origin_? = extractUri(anchor)
				withGood(element_?, origin_?) { (element, origin) =>
					element.copy(
						source = element.source.replace("/t", "/"),
						origin = origin,
						props = element.props - "width" - "height")
				}
			}
		val next_? = Selection(doc).all("a.next").wrap { selectNext("page") }

		withGood(elements_?, next_?) { _ ::: _ }
	}

	def wrap(doc: Document, pd: Pointer): List[Description] = Description.fromOr(pd.pagetype match {
		case "archive" => wrapArchive(doc)
		case "page" => wrapPage(doc)
	})
}
