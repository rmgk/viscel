package viscel.narration.narrators

import org.jsoup.nodes.{Document, Element}
import org.scalactic.{Accumulation, Good}
import viscel.narration.{Contents, Narrator, Queries}
import viscel.scribe.{ArticleRef, Link, Volatile, Vurl, WebContent}
import viscel.selection.{ReportTools, Selection}

import scala.collection.immutable.Set


object KatBox {

	val cores: Set[Narrator] = Set(
		("laslindas", "Las Lindas!", Some[Vurl]("http://laslindas.katbox.net/las-lindas/"))
	).map { case (_id, _name, _url) =>
		new Narrator {
			override def id: String = s"KatBox_${_id}"
			override def name: String = _name
			override def archive: List[WebContent] = List(Link(_url.getOrElse(s"http://${_id}.katbox.net/archive/"), Volatile))
			override def wrap(doc: Document, link: Link): Contents = {
				Selection(doc).many("span.archive-link a.webcomic-link").wrapFlat { anchor: Element =>
					// laslindas at least seems to miss some pages, just skip them
					if (anchor.childNodeSize() == 0) Good(List.empty)
					else {
						val vurl_? = Queries.extractURL(anchor)
						val img_? = Selection(anchor).unique("img").wrapOne(i => ReportTools.extract(i.absUrl("src")))
						Accumulation.withGood(img_?, vurl_?) { (img, vurl) =>
							List(ArticleRef(
								ref = img.replaceFirst("-\\d+x\\d+\\.", "."),
								origin = vurl,
								data = Map()))
						}
					}
				}.map(_.reverse)
			}
		}
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
