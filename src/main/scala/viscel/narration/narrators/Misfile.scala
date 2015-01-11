package viscel.narration.narrators

import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import org.scalactic.{ErrorMessage, Every, Or}
import viscel.narration.SelectUtil._
import viscel.narration.{Narrator, Selection}
import viscel.shared.Story
import viscel.shared.Story.{Chapter, More}

import scala.language.implicitConversions

object Misfile extends Narrator {
	def archive = More("http://www.misfile.com/archives.php?arc=1&displaymode=wide&", "archive") :: Nil

	def id: String = "NX_Misfile"

	def name: String = "Misfile"

	def wrapArchive(doc: Document): Or[List[Story], Every[ErrorMessage]] = {
		val chapters_? = Selection(doc).many("#comicbody a:matchesOwn(^Book #\\d+$)").wrapFlat { anchor =>
			withGood(elementIntoPointer("page")(anchor)) { pointer =>
				Chapter(anchor.ownText()) :: pointer :: Nil
			}
		}
		// the list of chapters is also the first page, wrap this directly
		val firstPage_? = wrapPage(doc)

		withGood(firstPage_?, chapters_?) { (page, chapters) =>
			Chapter("Book #1") :: page ::: chapters
		}
	}

	def wrapPage(doc: Document): Or[List[Story], Every[ErrorMessage]] = {
		val elements_? = Selection(doc)
			.unique(".comiclist table.wide_gallery")
			.many("[id~=^comic_\\d+$] .picture a").wrapEach { anchor =>
			val element_? = Selection(anchor).unique("img").wrapOne { imgIntoAsset }
			val origin_? = extractUri(anchor)
			withGood(element_?, origin_?) { (element, origin) =>
				element.copy(
					source = element.source.replace("/t", "/"),
					origin = origin,
					metadata = element.metadata - "width" - "height")
			}
		}
		val next_? = Selection(doc).all("a.next").wrap { selectNext("page") }

		append(elements_?, next_?)
	}

	def wrap(doc: Document, kind: String): List[Story] = storyFromOr(kind match {
		case "archive" => wrapArchive(doc)
		case "page" => wrapPage(doc)
	})
}
