package viscel.narration.narrators

import viscel.narration.Queries.{extractMore, queryImages, stringToURL}
import viscel.narration.{Narrator, Templates}
import viscel.selection.Selection

import scala.collection.immutable.Set


object KatBox {

	val cores: Set[Narrator] = Set(
		("laslindas", "Las Lindas!"),
		("cblue", "Caribbean Blue!"),
		("yosh", "Yosh!"),
		("anthronauts", "Anthronauts!"),
		("theeye", "The Eye of Ramalach!"),
		("draconia", "Draconia Chronicles!"),
		("imew", "iMew!"),
		("rascals", "Rascals!"),
		("knuckleup", "KnuckleUp!"),
		("projectzero", "Project Zero!"),
		("tinaofthesouth", "Tina of the South!"),
		("swashbuckled", "Swashbuckled!"),
		("dmfa", "DMFA!"),
		("uberquest", "UberQuest!"),
		("ourworld", "Our World!")).map { case (id, name) =>
		Templates.AP(s"KatBox_$id", s"[KB] $name", s"http://$id.katbox.net/archive",
			Selection(_).many("[rel=bookmark]").wrapEach(extractMore).map {_.reverse},
			queryImages(".webcomic-image img")
		)
	}
}
