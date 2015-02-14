package viscel.narration.narrators

import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import org.scalactic.{ErrorMessage, Every, Good, Or}
import viscel.compat.v1.{SelectionV1, Story}
import viscel.compat.v1.Story.More.{Archive, Kind, Page}
import viscel.compat.v1.Story.{Chapter, More}
import viscel.narration.SelectUtil._
import viscel.narration.NarratorV1

import scala.Predef.augmentString
import scala.language.implicitConversions

object Building12 extends NarratorV1 {
	def archive = More("http://www.building12.net/archives.htm", Archive) :: Nil

	def id: String = "NX_Building12"

	def name: String = "Building 12"

	def wrapIssue(doc: Document): Or[List[Story], Every[ErrorMessage]] = {
		val elements_? = SelectionV1(doc).many("a[href~=issue\\d+/.*\\.htm$]:has(img)").wrapEach { anchor =>
			val element_? = SelectionV1(anchor).unique("img").wrapOne { imgIntoAsset }
			val origin_? = extractUri(anchor)
			withGood(element_?, origin_?) { (element, origin) =>
				element.copy(
					source = element.source.replace("sm.", "."),
					origin = origin,
					metadata = element.metadata - "width" - "height")
			}
		}
		cons(Good(Chapter("issue\\d+".r.findFirstIn(doc.baseUri()).getOrElse("Unknown Issue"))), elements_?)
	}

	def wrap(doc: Document, kind: Kind): List[Story] = storyFromOr(kind match {
		case Archive => SelectionV1(doc).many("a[href~=issue\\d+\\.htm$]").wrapEach(elementIntoPointer(Page))
		case Page => wrapIssue(doc)
	})
}
