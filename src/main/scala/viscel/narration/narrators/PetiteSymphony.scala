package viscel.narration.narrators

import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import viscel.narration.SelectUtil._
import viscel.narration.{Narrator, Selection}
import viscel.shared.Story
import viscel.shared.Story.More

import scala.collection.immutable.Set

object PetiteSymphony {

	case class Generic(id: String, name: String, start: String) extends Narrator {
		override def archive: List[Story] = More(start, "") :: Nil
		override def wrap(doc: Document, pd: More): List[Story] = storyFromOr {
			val next_? = Selection(doc).optional("a.navi.navi-next").wrap(selectNext(""))
			val img_? = Selection(doc).unique("#comic-1 img").wrapEach(imgIntoAsset)
			withGood(img_?, next_?) { _ ::: _ }
		}
	}

	val cores: Set[Narrator] = Set(
		("goyoku", "Rascals Goyoku", "http://goyoku.petitesymphony.com/comic/goyoku-prologue1/"),
		("generation17", "Generation 17", "http://generation17.petitesymphony.com/comic/cover"),
		("seed", "Seed", "http://seed.petitesymphony.com/comic/seedchapter1"),
		("djandora", "Djandora", "http://djandora.petitesymphony.com/comic/intro"),
		("heid", "Heid", "http://heid.petitesymphony.com/c1p0-4/"),
		("knuckleup", "Knuckle Up", "http://knuckleup.petitesymphony.com/comic/knuckleup-prologue1"),
		("kickinrad", "Kickin Rad", "http://kickinrad.petitesymphony.com/comic/hell-no/"),
		("projectzero", "Project Zero", "http://projectzero.petitesymphony.com/comic/project-zero-cover/"),
		("retake", "Retake", "http://retake.petitesymphony.com/comic/retake-cover/"),
		("petsymph", "Petsymph", "http://petsymph.petitesymphony.com/comic/petsymph-wakey-wakey/"),
		("remnantsonata", "Remnant Sonata", "http://remnantsonata.petitesymphony.com/comic/rs-pg1/"),
		("ladycrimson", "Lady Crimson", "http://ladycrimson.petitesymphony.com/comic/ladycrimson_cover/"),
		("yearofthecow", "Year of the Cow", "http://yearofthecow.petitesymphony.com/comic/yotc-cover/")
	).map { case (id, name, start) => Generic(s"PetiteSymphony_$id", name, start) }

}
