package viscel.narration.narrators

import org.scalactic.Accumulation._
import viscel.narration.SelectMore.{extractMore, stringToURL}
import viscel.narration.{Queries, SelectMore, Templates}
import viscel.selection.Selection

import scala.collection.immutable.Set


object Snafu {

	def Snar(id: String, name: String, start: String) = Templates.AP(id, name, start,
		Selection(_).unique(".pagecontentbox").many("a").wrap {_.reverse.validatedBy(extractMore)},
		Queries.queryImage("img[src~=comics/\\d{6}]")
	)

	def cores = Set(
		("tw", "Training Wheels", "http://tw.snafu-comics.com/archive.php"),
		("snafu", "Snafu Comics", "http://www.snafu-comics.com/archive.php"),
		("naruto", "Naruto: Heroes Path", "http://naruto.snafu-comics.com/archive.php"),
		("titan", "Titan Sphere", "http://titan.snafu-comics.com/archive.php"),
		("braindead", "Brain Dead", "http://braindead.snafu-comics.com/archive.php"),
		("ft", "Forgotten Tower", "http://ft.snafu-comics.com/archive.php"),
		("dp", "Digital Purgatory", "http://dp.snafu-comics.com/archive.php"),
		("kof", "King of Fighters Doujinshi", "http://kof.snafu-comics.com/archive.php"),
		("ppg", "PowerPuff Girl Doujinshi", "http://ppg.snafu-comics.com/archive.php"),
		("satans", "Satan's Excrement", "http://satans.snafu-comics.com/archive.php"),
		("zim", "Invader Zim", "http://zim.snafu-comics.com/archive.php"),
		("mypanda", "MyPanda", "http://mypanda.snafu-comics.com/archive.php"),
		("bunnywith", "Bunnywith", "http://bunnywith.snafu-comics.com/archive.php"),
		("grim", "Grim Tales", "http://grim.snafu-comics.com/archive.php"),
		("tin", "Tin The Incompetent Ninja", "http://tin.snafu-comics.com/archive.php"),
		("ea", "Ever After", "http://ea.snafu-comics.com/archive.php"),
		// image urls dead
		//("awful", "Awful Comic", "http://awful.snafu-comics.com/archive.php"),
		("sf", "Sticky Floors", "http://sf.snafu-comics.com/archive.php"),
		("skullboy", "Fluffy Doom", "http://skullboy.snafu-comics.com/archive.php"),
		("league", "The League", "http://league.snafu-comics.com/archive.php"),
		("sugar", "Sugar Bits", "http://sugar.snafu-comics.com/archive.php"),
		("soul", "Soul Frontier", "http://soul.snafu-comics.com/archive.php")
	).map { case (id, name, url) => Snar(s"Snafu_$id", s"[snafu] $name", url) }

}
