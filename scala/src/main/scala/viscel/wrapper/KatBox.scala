package viscel.wrapper

import org.jsoup.nodes.Document
import viscel.core.Core
import viscel.description._
import viscel.wrapper.Util._


object KatBox {

	case class Generic(shortId: String, name: String) extends Core {

		def archive = Pointer(s"http://$shortId.katbox.net/archive", "archive") :: Nil

		val id: String = s"KatBox_$shortId"

		def wrap(doc: Document, pd: Pointer): List[Description] = Description.fromOr(pd.pagetype match {
			case "archive" =>
				Selection(doc).many("[rel=bookmark]").wrapEach(anchorIntoPointer("page")).map { _.reverse }
			case "page" => queryImages(doc, ".webcomic-image img")
		})
	}

	def cores(): Set[Core] = Set (
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
