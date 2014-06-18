package viscel.wrapper

import org.jsoup.nodes.Document
import viscel.core._
import viscel.description._
import viscel.wrapper.Util._
import org.scalactic.Accumulation._

object PetiteSymphony {

	case class Generic(id: String, name: String, start: String) extends Core {
		override def archive: Description = Chapter("") :: Pointer(start, "")
		override def wrap(doc: Document, pd: Pointer): Description = Description.fromOr {
			val next_? = Selection(doc).optional("a.navi.navi-next").wrap(selectNext(""))
			val img_? = Selection(doc).unique("#comic-1 img").wrapOne(imgIntoStructure)
			withGood(img_?, next_?) { _ :: _}
		}
	}

	def cores(): Set[Core] = Set(
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
