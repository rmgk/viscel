package viscel.narration.narrators

import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import org.scalactic.{ErrorMessage, Every, Good, Or}
import viscel.narration.SelectUtil._
import viscel.narration.{Narrator, Selection}
import viscel.shared.Story
import viscel.shared.Story.More.{Page, Archive, Kind}
import viscel.shared.Story.{Chapter, More}

import scala.Predef.augmentString
import scala.language.implicitConversions

object Building12 extends Narrator {
	def archive = More("http://www.building12.net/archives.htm", Archive) :: Nil

	def id: String = "NX_Building12"

	def name: String = "Building 12"

	def wrapIssue(doc: Document): Or[List[Story], Every[ErrorMessage]] = {
		val elements_? = Selection(doc).many("a[href~=issue\\d+/.*\\.htm$]:has(img)").wrapEach { anchor =>
			val element_? = Selection(anchor).unique("img").wrapOne { imgIntoAsset }
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
		case Archive => Selection(doc).many("a[href~=issue\\d+\\.htm$]").wrapEach(elementIntoPointer(Page))
		case Page => wrapIssue(doc)
	})
}
