package viscel.narration.narrators

import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import org.scalactic.{ErrorMessage, Every, Or}
import viscel.narration.SelectUtil._
import viscel.narration.{Metarrator, Narrator, Selection}
import viscel.shared.Story.More
import viscel.shared.Story.More.{Archive, Kind, Page}
import viscel.shared.{Story, ViscelUrl}

import scala.collection.immutable.Set


object Snafu {

	case class Snar(override val id: String, override val name: String, start: ViscelUrl) extends Narrator {
		def archive = More(start, Archive) :: Nil

		def wrap(doc: Document, kind: Kind): List[Story] = storyFromOr(kind match {
			case Archive => Selection(doc).unique(".pagecontentbox").many("a").wrap { anchors =>
				anchors.reverse.validatedBy(elementIntoPointer(Page))
			}
			case Page => queryImage("img[src~=comics/\\d{6}]")(doc)
		})
	}

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
