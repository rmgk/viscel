package viscel.narration.narrators

import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import org.scalactic.{ErrorMessage, Every, Or}
import viscel.compat.v1.Story
import viscel.compat.v1.Story.More
import viscel.compat.v1.Story.More.{Archive, Kind, Page}
import viscel.narration.SelectUtil._
import viscel.narration.{NarratorV1, Selection}

import scala.language.implicitConversions

object Candi extends NarratorV1 {
	def archive = More("http://candicomics.com/archive.html", Archive) :: Nil

	def id: String = "NX_Candi"

	def name: String = "Candi"

	def wrapArchive(doc: Document): Or[List[Story], Every[ErrorMessage]] = {
		val volumes_? = Selection(doc).many("#candimidd > p:nth-child(2) a")
			.wrapEach { elementIntoPointer(Story.More.Issue) }
		// the list of volumes is also the first volume, wrap this directly
		val firstVolume_? = wrapVolume(doc)

		withGood(firstVolume_?, volumes_?) { (first, volumes) =>
			first ::: volumes.drop(1)
		}
	}

	def wrapVolume(doc: Document): Or[List[Story], Every[ErrorMessage]] =
		Selection(doc).many("#candimidd > table > tbody > tr > td:nth-child(2n) a").wrapFlat { elementIntoChapterPointer(Page) }


	def wrap(doc: Document, kind: Kind): List[Story] = storyFromOr(kind match {
		case Archive => wrapArchive(doc)
		case Story.More.Issue => wrapVolume(doc)
		case Page => queryImageNext("#comicplace > span > img", "#comicnav a:has(img#next_day2)", Page)(doc)
	})
}
