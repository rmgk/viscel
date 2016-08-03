package viscel.narration.narrators

import viscel.narration.Queries.queryImageNext
import viscel.narration.{Narrator, Templates}

import scala.collection.immutable.Set

object PetiteSymphony {

	def cores: Set[Narrator] = Set(
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
	).map { case (id, name, start) => Templates.SF(s"PetiteSymphony_$id", s"[PS] $name", start, queryImageNext("#comic-1 img", "a.navi.navi-next")) }

}
