package viscel.narration.narrators

import java.net.URL

import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import org.scalactic._
import viscel.narration.Queries._
import viscel.narration.{Queries, Data, Metarrator}
import viscel.scribe.narration.SelectMore._
import viscel.scribe.narration.{Asset, More, Narrator, Selection, Story}
import viscel.scribe.report.Report
import viscel.scribe.report.ReportTools._

import scala.Predef.augmentString

object Fakku {

	val baseURL = new URL("https://www.fakku.net/")
	val extractID = ".*/(?:manga|doujinshi)/([^/]+)/read".r
	def makeID(part: String) = s"Fakku_${part.replaceAll("\\W", "_")}"
	

	case class FKU(override val id: String, override val name: String, url: String, collection: Boolean = false) extends Narrator {
		override def archive: List[Story] = More(url, data = if (collection) List("col") else Nil) :: Nil

		val findStr = "window.params.thumbs = "
		val extractPos = ".*\\D(\\d+)\\.\\w+".r

		override def wrap(doc: Document, more: More): List[Story] Or Every[Report] = more match {
			case More(_, _, Nil) => wrapChapter(doc)
			case _ => wrapCollection(doc)
		}

		def wrapCollection(doc: Document): List[Story] Or Every[Report] = {
			val next_? = moreData(queryNext("#pagination > div.results > a:contains(Next)")(doc), "col")
			val chapters_? = queryChapterArchive("#content > div > div.content-meta > h2 > a.content-title")(doc).map(_.map{
				case m @ More(u, _, _) => m.copy(loc = s"$u/read")
				case o => o
			})
			append(chapters_?, next_?)
		}

		def wrapChapter(doc: Document): List[Story] Or Every[Report] = Selection(doc).many("head script").wrap { scripts =>
			val jsSrc = scripts.map(_.html()).mkString("\n")
			val start = jsSrc.indexOf(findStr) + findStr.length
			val end = jsSrc.indexOf("\n", start) - 1
			extract { upickle.read[List[String]](jsSrc.substring(start, end)).map(_.replaceAll("thumbs/(\\d+).thumb", "images/$1")).map(new URL(baseURL, _).toString) }.map(_.map { url =>
				val extractPos(pos) = url
				Data.Article(url, s"${ doc.baseUri() }#page=$pos")
			})
		}
	}

	def create(name: String, url: String): FKU = {
		val _id = url match {
			case extractID(eid) => makeID(eid)
			case _ => throw new IllegalArgumentException(s"could not find id for $url")
		}
		FKU(_id, s"[FK] ${ name }", url)
	}


	object Meta extends Metarrator[FKU]("Fakku") {

		override def unapply(url: String): Option[URL] = {
			if (new URL(url).getHost == baseURL.getHost) Some(new URL(url)) else None
		}

		def wrap(doc: Document): List[FKU] Or Every[Report] = doc.baseUri() match {
			case rex"https://www.fakku.net/collections/($id[^/]*)" =>
				val name_? = Selection(doc).unique("#page > div.attribute-header.collection > h1").wrapOne(e => Good(e.text()))
				name_?.map(name => FKU(makeID(id), name, doc.baseUri(), collection = true) :: Nil)
			case other =>
				val current = Selection(doc).all("#content > div.content-wrap")
				val currentUrl_? = current.optional("a.button.green").wrapEach(e => Good(e.attr("abs:href")))
				val currentName_? = current.optional("h1[itemprop=name]").wrapEach(e => Good(e.text()))

				val rows_? = Selection(doc).all(".content-row a.content-title").get.map(_.map { a => (a.text, a.attr("abs:href") + "/read") })

				val pairs = append(withGood(currentName_?, currentUrl_?) { _ zip _ }.recover(_ => Nil), rows_?)

				pairs.map(_.map((create _).tupled))
		}
	}

}
