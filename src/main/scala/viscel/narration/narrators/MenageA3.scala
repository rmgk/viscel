package viscel.narration.narrators

import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import org.scalactic.{ErrorMessage, Every, Or}
import viscel.narration.SelectUtil._
import viscel.narration.{Narrator, Selection}
import viscel.shared.Story
import viscel.shared.Story.More.{Archive, Page, Kind}
import viscel.shared.Story.{Chapter, More}

import scala.language.implicitConversions

object MenageA3 extends Narrator {
	def archive = More("http://www.ma3comic.com/archive/volume1", Archive) :: Nil

	def id: String = "NX_MenageA3"

	def name: String = "Ménage à 3"

	def wrapArchive(doc: Document): Or[List[Story], Every[ErrorMessage]] = {
		val volumes_? = Selection(doc).many("#archive_browse a[href~=.*archive/volume\\d+$]")
			.wrapEach { elementIntoPointer(Story.More.Issue) }
		// the list of volumes is also the first volume, wrap this directly
		val firstVolume_? = wrapVolume(doc)

		withGood(firstVolume_?, volumes_?) { (first, volumes) =>
			Chapter(s"Volume 1") :: first ::: volumes.drop(1).zipWithIndex.flatMap { case (v, i) => Chapter(s"Volume ${ i + 2 }") :: v :: Nil }
		}
	}

	def wrapVolume(doc: Document): Or[List[Story], Every[ErrorMessage]] =
		Selection(doc)
			.unique("#archive_chapters")
			.many("a[href~=/strips-ma3/]").wrapEach { elementIntoPointer(Page) }


	def wrap(doc: Document, kind: Kind): List[Story] = storyFromOr(kind match {
		case Archive => wrapArchive(doc)
		case Story.More.Issue => wrapVolume(doc)
		case Page => queryImage("#cc img")(doc)
	})
}
