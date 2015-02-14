package viscel.server

import spray.http._
import upickle.Writer
import viscel.Log
import viscel.narration.Narrators
import viscel.scribe.Scribe
import viscel.scribe.database.Neo
import viscel.scribe.narration.{ Asset => SAsset, Page}
import viscel.shared.JsonCodecs.stringMapW
import viscel.shared.{Description, Chapter, Article, Content, Gallery}
import viscel.store.User

import scalatags.Text.attrs.{`type`, content, href, name, rel, src, title}
import scalatags.Text.implicits.{Tag, stringAttr, stringFrag}
import scalatags.Text.tags.{body, head, html, link, meta, script}
import scalatags.Text.{Modifier, RawFrag}


class ServerPages(scribe: Scribe) {

	import scribe.books._

	def neo: Neo = scribe.neo

	def narration(id: String): Option[Content] = neo.tx { implicit ntx =>
		(Narrators.get(id) match {
			case None => findExisting(id)
			case Some(nar) => Some(findAndUpdate(nar))
		}).map(book => {
			val content: (Int, List[Article], List[Chapter])= book.pages().reverse.foldLeft((0, List[Article](), List[Chapter]())) {
				case (state@(pos, assets, chapters), Page(SAsset(source, origin, 0, data), blob)) =>
					val asset = Article(
						source = source map (_.toString),
						origin = origin map (_.toString),
						data = data.sliding(2, 2).map(l => (l(0), l(1))).toMap,
						blob = blob map (_.sha1),
						mime = blob map (_.mime))
					(pos + 1, asset :: assets, if (chapters.isEmpty) List(Chapter("", 0)) else chapters)
				case (state@(pos, assets, chapters), Page(SAsset(None, None, 1, name :: data), None)) =>
					val chapter = Chapter(name, pos)
					(pos, assets, chapter :: chapters)
				case (state@(pos, assets, chapters), page) =>
					Log.error(s"unhandled page $page")
					state
			}

			Content(Gallery.fromList(content._2.reverse), content._3)

		})
	}

	def narrations(): HttpResponse =
		jsonResponse(neo.tx { implicit ntx =>
			scribe.books.all().map(b => Description(b.id, b.name, b.size))
		})


	val path_css: String = "css"
	val path_js: String = "js"


	def makeHtml(stuff: Modifier*): Tag =
		html(
			head(
				title := "Viscel",
				link(href := path_css, rel := "stylesheet", `type` := MediaTypes.`text/css`.toString()),
				meta(name := "viewport", content := "width=device-width, initial-scale=1, user-scalable=yes")),

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

	//	def stats(stats: Stats)(implicit ntx: Ntx): HttpResponse = jsonResponse {
	//		val cn = Config.get()
	//		Map[String, Long](
	//			"Downloaded" -> cn.downloaded,
	//			"Downloads" -> cn.downloads,
	//			"Compressed downloads" -> cn.downloadsCompressed,
	//			"Failed downloads" -> cn.downloadsFailed,
	//			"Narrations" -> ntx.nodes(label.Collection).size,
	//			"Chapters" -> ntx.nodes(label.Chapter).size,
	//			"Assets" -> ntx.nodes(label.Asset).size,
	//			"Uptime" -> stats.uptime.toSeconds,
	//			"Total requests" -> stats.totalRequests,
	//			"Open requests" -> stats.openRequests,
	//			"Max open requests" -> stats.maxOpenRequests,
	//			"Total connections" -> stats.totalConnections,
	//			"Open connections" -> stats.openConnections,
	//			"Max open connections" -> stats.maxOpenConnections,
	//			"Requests timed out" -> stats.requestTimeouts)
	//	}

}
