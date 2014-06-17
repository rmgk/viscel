package viscel.wrapper

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import org.scalactic._
import viscel.core._
import viscel.description._

import scala.collection.JavaConversions._

object CitrusSaburoUta extends Core with WrapperTools with StrictLogging {
	def archive = Pointer("http://mangafox.me/manga/citrus_saburo_uta/", "archive")

	def id: String = "Mangafox_Citrus"

	def name: String = "CITRUS (SABURO UTA)"

	def wrapArchive(doc: Document, pd: Pointer): Structure Or Every[ErrorMessage] = {
		selectSome(doc, ".chlist li div :has(.tips):has(.title)").flatMap { chapters =>
			chapters.reverse.validatedBy { chapter =>
				val title_? = selectUnique(chapter, ".title").map(_.ownText())
				val link_text_? = selectUnique(chapter, "a.tips").map(anchor => (anchor.attr("abs:href"), anchor.ownText()))
				withGood(title_?, link_text_?) { (title, link_text) =>
					Structure(payload = Chapter(s"${link_text._2} $title"), children = Pointer(link_text._1, "page") :: Nil)
				}
			}
		}.map { chapters => Structure(children = chapters) }
	}

	def wrapPage(doc: Document, pd: Pointer): Structure Or Every[ErrorMessage] = {
		val next = doc.select("#top_bar .next_page:not([onclick])").toSeq.headOption.fold(EmptyDescription: Description) { anchor =>
			Pointer(loc = anchor.attr("abs:href"), pagetype = "page")
		}
		selectUnique(doc, "#viewer img").map { imgToElement }.map{ img =>
			Structure(payload = img.copy(props = img.props -- List("width", "height")), next = next) }
	}

	def wrap(doc: Document, pd: Pointer): Description = Description.fromOr(pd.pagetype match {
		case "archive" => wrapArchive(doc, pd)
		case "page" => wrapPage(doc, pd)
	})
}
