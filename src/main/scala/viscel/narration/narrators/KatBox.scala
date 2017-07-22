package viscel.narration.narrators

import viscel.narration.Queries.queryImageInAnchor
import viscel.narration.{Narrator, Templates}

import scala.collection.immutable.Set


object KatBox {

	val cores: Set[Narrator] = Set(
		("laslindas", "Las Lindas!", "http://laslindas.katbox.net/comic/day-one/")
	).map { case (id, name, url) =>
			Templates.SimpleForward(s"KatBox_$id", s"[KB] $name", url,
				queryImageInAnchor("img[data-webcomic-parent]")
			)
	}

//	val cores: Set[Narrator] = Set(
//		("addictivescience", "Addictive Science"),
//		("ai", "Artificial Incident"),
//		("anthronauts", "Anthronauts!"),
//		("cblue", "Caribbean Blue!"),
//		("desertfox", "Desert Fox"),
//		("dmfa", "DMFA!"),
//		("draconia", "Draconia Chronicles!"),
//		("falsestart", "False Start"),
//		("iba", "Itsy Bitsy Adventures"),
//		("imew", "iMew!"),
//		("knuckleup", "KnuckleUp!"),
//		("laslindas", "Las Lindas!"),
//		("mousechievous", "Mousechievous"),
//		("ourworld", "Our World!"),
//		("paprika", "Paprika"),
//		("peterandcompany", "Peter & Company"),
//		("peterandwhitney", "Peter & Whitney"),
//		("pmp", "Practice makes Perfect"),
//		("projectzero", "Project Zero!"),
//		("rascals", "Rascals!"),
//		("swashbuckled", "Swashbuckled!"),
//		("theeye", "The Eye of Ramalach!"),
//		("tinaofthesouth", "Tina of the South!"),
//		("uberquest", "UberQuest!"),
//		("yosh", "Yosh!")
//	).map { case (id, name) =>
//		Templates.ArchivePage(s"KatBox_$id", s"[KB] $name", s"http://$id.katbox.net/archive/",
//			Selection(_).many("[rel=bookmark]").wrapEach(extractMore).map {_.reverse},
//			queryImages(".webcomic-image img")
//		)
//	}
}
