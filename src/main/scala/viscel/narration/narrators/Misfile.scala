package viscel.narration.narrators

import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import org.scalactic.{ErrorMessage, Every, Or}
import viscel.compat.v1.{SelectionV1, Story}
import viscel.compat.v1.Story.More.{Archive, Kind, Page}
import viscel.compat.v1.Story.{Chapter, More}
import viscel.narration.SelectUtilV1._
import viscel.narration.NarratorV1

import scala.language.implicitConversions

object Misfile extends NarratorV1 {
	def archive = More("http://www.misfile.com/archives.php?arc=1&displaymode=wide&", Archive) :: Nil

	def id: String = "NX_Misfile"

	def name: String = "Misfile"

	def wrapArchive(doc: Document): Or[List[Story], Every[ErrorMessage]] = {
		val chapters_? = SelectionV1(doc).many("#comicbody a:matchesOwn(^Book #\\d+$)").wrapFlat { anchor =>
			withGood(elementIntoPointer(Page)(anchor)) { pointer =>
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
		val elements_? = SelectionV1(doc)
			.unique(".comiclist table.wide_gallery")
			.many("[id~=^comic_\\d+$] .picture a").wrapEach { anchor =>
			val element_? = SelectionV1(anchor).unique("img").wrapOne { imgIntoAsset }
			val origin_? = extractUri(anchor)
			withGood(element_?, origin_?) { (element, origin) =>
				element.copy(
					source = element.source.replace("/t", "/"),
					origin = origin,
					metadata = element.metadata - "width" - "height")
			}
		}
		val next_? = SelectionV1(doc).all("a.next").wrap { selectNext(Page) }

		append(elements_?, next_?)
	}

	def wrap(doc: Document, kind: Kind): List[Story] = storyFromOr(kind match {
		case Archive => wrapArchive(doc)
		case Page => wrapPage(doc)
	})
}
