package viscel.narration.narrators


import viscel.narration.Queries.stringToURL
import viscel.narration.{Queries, Templates}

import scala.collection.immutable.Set

object CloneManga {

	def Clone(id: String, name: String, start: String) = Templates.SF(id, name, start,
		Queries.queryImageNext(".subsectionContainer img", ".subsectionContainer > a:first-child"))

	def cores = Set(
		("graveyard_snax", "Vampire Bride", "http://manga.clone-army.org/viewer.php?lang=english&series=graveyard_snax&page=1"),
		("momoka", "Momoka Corner", "http://manga.clone-army.org/viewer.php?series=momoka&page=1"),
		("witches_castle", "§II - The Castle", "http://manga.clone-army.org/viewer.php?series=witches_castle&page=1"),
		("maria_karen", "Karen", "http://manga.clone-army.org/viewer.php?series=maria_karen&page=1"),
		("anm", "April & May", "http://manga.clone-army.org/viewer.php?series=anm&page=1"),
		("witches_fragments", "§IIII - Recovery Fragments", "http://manga.clone-army.org/viewer.php?series=witches_fragments&page=1"),
		("gwupo", "Gwupo & Company", "http://manga.clone-army.org/viewer.php?series=gwupo&page=1"),
		("archived_cindy", "Cotton Candy Cindy (2001)", "http://manga.clone-army.org/viewer.php?series=archived_cindy&page=1"),
		("graveyard_metalfist_9", "Abandoned/Cut Material", "http://manga.clone-army.org/viewer.php?lang=english&series=graveyard_metalfist_9&page=1"),
		("pantsuit", "Puella Pantsuit Magica", "http://manga.clone-army.org/viewer.php?series=pantsuit&page=1"),
		("maria_doll", "Doll & Maker", "http://manga.clone-army.org/viewer.php?series=maria_doll&page=1"),
		("t42r", "Tomoyo42's Room", "http://manga.clone-army.org/viewer.php?series=t42r&page=1"),
		("archived_pakman", "Pakman Saga (1996)", "http://manga.clone-army.org/viewer.php?series=archived_pakman&page=1"),
		("witches", "§I - Subjkt Kine Pnny-Biengg", "http://manga.clone-army.org/viewer.php?series=witches&page=1"),
		("metalfist", "Metal Fist", "http://manga.clone-army.org/viewer.php?series=metalfist&page=1"),
		("maria_star", "Parallel Star", "http://manga.clone-army.org/viewer.php?series=maria_star&page=1"),
		("kanami", "Kanami", "http://manga.clone-army.org/viewer.php?series=kanami&page=1"),
		("pxi", "Paper Eleven (PXI)", "http://manga.clone-army.org/viewer.php?series=pxi&page=1"),
		("cupcake", "Cupcake Universe", "http://manga.clone-army.org/viewer.php?series=cupcake&page=1"),
		("archived_phantasmagory", "Phantasmagory (2005)", "http://manga.clone-army.org/viewer.php?series=archived_phantasmagory&page=1"),
		("nnn", "NNN", "http://manga.clone-army.org/viewer.php?series=nnn&page=1"),
		("jis", "June in Summer", "http://manga.clone-army.org/viewer.php?series=jis&page=1"),
		("nana", "Nana's Everyday Life", "http://manga.clone-army.org/viewer.php?series=nana&page=1"),
		("penny", "Penny Tribute", "http://manga.clone-army.org/viewer.php?series=penny&page=1"),
		("cupcake_shorts", "Magical Girl Cupcake", "http://manga.clone-army.org/viewer.php?series=cupcake_shorts&page=1"),
		("graveyard_cupcake", "Cupcake", "http://manga.clone-army.org/viewer.php?lang=english&series=graveyard_cupcake&page=1"),
		("hh", "H.H.", "http://manga.clone-army.org/viewer.php?series=hh&page=1"),
		("misc", "Misc. Comics", "http://manga.clone-army.org/viewer.php?series=misc&page=1"),
		("witches_loops", "§III - Closed Circles", "http://manga.clone-army.org/viewer.php?series=witches_loops&page=1"),
		("snax", "My Shut-In Vampire Princess", "http://manga.clone-army.org/viewer.php?series=snax&page=1")
	).map { case (id, name, url) => Clone(s"CloneManga_$id", s"[CM] $name", url) }

}
