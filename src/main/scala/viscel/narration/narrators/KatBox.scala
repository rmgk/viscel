package viscel.narration.narrators

import org.jsoup.nodes.Document
import viscel.shared.Story
import Story.More
import viscel.narration.Util._
import viscel.narration.{Narrator, Selection}

import scala.collection.immutable.Set


object KatBox {

	case class Generic(shortId: String, name: String) extends Narrator {

		def archive = More(s"http://$shortId.katbox.net/archive", "archive") :: Nil

		val id: String = s"KatBox_$shortId"

		def wrap(doc: Document, pd: More): List[Story] = Story.fromOr(pd.pagetype match {
			case "archive" =>
				Selection(doc).many("[rel=bookmark]").wrapEach(elementIntoPointer("page")).map { _.reverse }
			case "page" => queryImages(doc, ".webcomic-image img")
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
