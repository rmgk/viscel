package viscel.server

import spray.http._
import upickle.Writer
import viscel.narration.Narrators
import viscel.narration.SelectUtil.stringToVurl
import viscel.scribe.Scribe
import viscel.scribe.database.Neo
import viscel.scribe.narration.{Asset, Page}
import viscel.shared.JsonCodecs.stringMapW
import viscel.compat.v1.Story.{Content, Description}
import viscel.compat.v1.Story
import viscel.shared.{Gallery}
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
			val story: List[Story] = book.pages().map {
				case Page(Asset(Some(source), Some(origin), 0, data), blob) =>
					Story.Asset(
						source.toString,
						origin.toString,
						data.sliding(2, 2).map(l => (l(0), l(1))).toMap,
						blob.map(b => Story.Blob(b.sha1, b.mime)))
				case Page(Asset(None, None, 1, name :: data), None) =>
					Story.Chapter(
						name,
						data.sliding(2, 2).map(l => (l(0), l(1))).toMap)
			}
			val all: (Int, List[Story.Asset], List[(Int, Story.Chapter)]) =
				story.reverse.foldLeft((0, List[Story.Asset](), List[(Int, Story.Chapter)]())) {
					case (state@(pos, assets, chapters), asset@Story.Asset(_, _, _, _)) =>
						(pos + 1, asset :: assets, if (chapters.isEmpty) List((0, Story.Chapter(""))) else chapters)
					case (state@(pos, assets, chapters), chapter@Story.Chapter(_, _)) => (pos, assets, (pos, chapter) :: chapters)
					case (state, _) => state
				}
			Content(Gallery.fromList(all._2.reverse), all._3)

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
