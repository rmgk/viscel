package viscel.narration.narrators

import org.jsoup.nodes.Document
import viscel.narration.SelectUtil._
import viscel.narration.{Narrator, Selection}
import viscel.shared.Story
import viscel.shared.Story.More
import viscel.shared.Story.More.{Archive, Kind, Page}

import scala.collection.immutable.Set


object KatBox {

	case class Generic(shortId: String, name: String) extends Narrator {

		def archive = More(s"http://$shortId.katbox.net/archive", Archive) :: Nil

		val id: String = s"KatBox_$shortId"

		def wrap(doc: Document, kind: Kind): List[Story] = storyFromOr(kind match {
			case Archive => Selection(doc).many("[rel=bookmark]").wrapEach(elementIntoPointer(Page)).map { _.reverse }
			case Page => queryImages(".webcomic-image img")(doc)
		})
	}

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
		("ourworld", "Our World!")).map((Generic.apply _).tupled)
}
