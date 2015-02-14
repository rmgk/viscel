package viscel.server

import spray.can.server.Stats
import spray.http._
import upickle.Writer
import viscel.Log
import viscel.narration.{AssetKind, Narrators}
import viscel.scribe.Scribe
import viscel.scribe.database.{Neo, label}
import viscel.scribe.narration.{Asset => SAsset, Page}
import viscel.shared.JsonCodecs.stringMapW
import viscel.shared.{Article, Chapter, Content, Description, Gallery}
import viscel.store.User

import scala.Predef.ArrowAssoc
import scala.collection.immutable.Map
import scalatags.Text.attrs.{`type`, content, href, name => attrname, rel, src, title}
import scalatags.Text.implicits.{Tag, stringAttr, stringFrag}
import scalatags.Text.tags.{body, head, html, link, meta, script}
import scalatags.Text.{Modifier, RawFrag}

class ServerPages(scribe: Scribe) {

	import scribe.books._

	def neo: Neo = scribe.neo

	object ArticlePage {
		def unapply(page: Page): Option[Article] = page match {
			case Page(SAsset(source, origin, AssetKind.article, data), blob) =>
				Some(Article(
					source = source map (_.toString),
					origin = origin map (_.toString),
					data = data.sliding(2, 2).map(l => (l(0), l(1))).toMap,
					blob = blob map (_.sha1),
					mime = blob map (_.mime)))
			case _ => None
		}
	}

	object ChapterPage {
		def unapply(page: Page): Option[String] = page match {
			case Page(SAsset(None, None, AssetKind.chapter, name :: data), None) => Some(name)
			case _ => None
		}
	}

	def narration(id: String): Option[Content] = neo.tx { implicit ntx =>
		(Narrators.get(id) match {
			case None => findExisting(id)
			case Some(nar) => Some(findAndUpdate(nar))
		}).map(book => {
			val content: (Int, List[Article], List[Chapter]) = book.pages().reverse.foldLeft((0, List[Article](), List[Chapter]())) {
				case (state@(pos, assets, chapters), ArticlePage(article)) =>
					(pos + 1, article :: assets, if (chapters.isEmpty) List(Chapter("", 0)) else chapters)
				case (state@(pos, assets, chapters), ChapterPage(name)) =>
					(pos, assets, Chapter(name, pos) :: chapters)
				case (state@(pos, assets, chapters), page) =>
					Log.error(s"unhandled page $page")
					state
			}

			Content(Gallery.fromList(content._2.reverse), content._3)

		})
	}

	def narrations(): HttpResponse =
		jsonResponse {
			val books = neo.tx { implicit ntx =>
				scribe.books.all().map(b => Description(b.id, b.name, b.size(0)))
			}
			val known = books.map(_.id).toSet
			val nars = Narrators.all.filterNot(n => known.contains(n.id)).map(n => Description(n.id, n.name, 0))
			nars.toList reverse_::: books
		}


	val path_css: String = "css"
	val path_js: String = "js"


	def makeHtml(stuff: Modifier*): Tag =
		html(
			head(
				title := "Viscel",
				link(href := path_css, rel := "stylesheet", `type` := MediaTypes.`text/css`.toString()),
				meta(attrname := "viewport", content := "width=device-width, initial-scale=1, user-scalable=yes")),

			body("if nothing happens, your javascript does not work"),
			script(src := path_js)
		)(stuff: _*)


	val fullHtml: Tag = makeHtml(script(RawFrag(s"Viscel().main()")))

	val landing: HttpResponse = HttpResponse(entity = HttpEntity(
		ContentType(MediaTypes.`text/html`, HttpCharsets.`UTF-8`),
		"<!DOCTYPE html>" + fullHtml.render))

	def jsonResponse[T: Writer](value: T): HttpResponse = HttpResponse(entity = HttpEntity(
		ContentType(MediaTypes.`application/json`, HttpCharsets.`UTF-8`),
		upickle.write(value)))

	def bookmarks(user: User): HttpResponse = jsonResponse(user.bookmarks)

	def stats(stats: Stats): HttpResponse = jsonResponse {
		val cn = scribe.cfg
		scribe.neo.tx { implicit ntx =>
			Map[String, Long](
				"Downloaded" -> cn.downloaded,
				"Downloads" -> cn.downloads,
				"Compressed downloads" -> cn.downloadsCompressed,
				"Failed downloads" -> cn.downloadsFailed,
				"Books" -> ntx.nodes(label.Book).size,
				"Assets" -> ntx.nodes(label.Asset).size,
				"Uptime" -> stats.uptime.toSeconds,
				"Total requests" -> stats.totalRequests,
				"Open requests" -> stats.openRequests,
				"Max open requests" -> stats.maxOpenRequests,
				"Total connections" -> stats.totalConnections,
				"Open connections" -> stats.openConnections,
				"Max open connections" -> stats.maxOpenConnections,
				"Requests timed out" -> stats.requestTimeouts)
		}
	}

}
