package viscel.narration.narrators

import org.jsoup.nodes.{Document, Element}
import org.scalactic.{Accumulation, Good}
import viscel.narration.{Contents, Narrator, Queries}
import viscel.scribe.{ArticleRef, Link, Volatile, Vurl, WebContent}
import viscel.selection.{ReportTools, Selection}

import scala.collection.immutable.Set


object KatBox {

	val cores: Set[Narrator] = Set[(String, String, Option[Vurl])](
		("addictivescience", "Addictive Science", None),
		("ai", "Artificial Incident", None),
		("cblue", "Caribbean Blue", None),
		("desertfox", "Desert Fox", None),
		("dmfa", "DMFA", None),
		("draconia", "Draconia Chronicles", None),
		("falsestart", "False Start", None),
		("iba", "Itsy Bitsy Adventures", None),
		("imew", "iMew", None),
		("laslindas", "Las Lindas!", Some[Vurl]("http://laslindas.katbox.net/las-lindas/")),
		("rixie", "Debunkers", None),
		("oasis", "Oasis", None),
		("ourworld", "Our World", None),
		("paprika", "Paprika", None),
		("peterandcompany", "Peter and Company", None),
		("peterandwhitney", "Peter and Whitney", None),
		("pmp", "Practice Makes Perfect", None),
		("rascals", "Rascals", None),
		("theeye", "The Eye of Ramalach", None),
		("tinaofthesouth", "Tina of the South", None),
		("uberquest", "Uber Quest", None),
		("yosh", "Yosh!", None),
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
}
