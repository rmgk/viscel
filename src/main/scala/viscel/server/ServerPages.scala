package viscel.server

import org.neo4j.graphdb.Node
import spray.can.server.Stats
import spray.http._
import upickle.Writer
import viscel.database.Util.listCollections
import viscel.database.{Ntx, Traversal, label}
import viscel.shared.Story
import viscel.shared.Story.Narration
import viscel.store.{Config, Collection, Coin, User}

import scala.Predef.any2ArrowAssoc
import scala.collection.immutable.Map
import scalatags.Text.attrs.{`type`, href, id, rel, src, title}
import scalatags.Text.implicits.{stringAttr, stringFrag}
import scalatags.Text.tags.{body, div, head, html, link, script}
import scalatags.Text.{RawFrag, TypedTag}



object ServerPages {
	val path_css: String = "/css"
	val path_js: String = "/js"

	val fullHtml: TypedTag[String] =
		html(
			head(
				link(href := path_css, rel := "stylesheet", `type` := MediaTypes.`text/css`.toString()),
				title := "Viscel"),
			body(id := "index")(div("this page is generated via javascript")),
			script(src := path_js),
			script(RawFrag(s"Viscel().main()")))

	val landing: HttpResponse = HttpResponse(entity = HttpEntity(
		ContentType(MediaTypes.`text/html`, HttpCharsets.`UTF-8`),
		"<!DOCTYPE html>" + fullHtml.render))

	def jsonResponse[T: Writer](value: T): HttpResponse = HttpResponse(entity = HttpEntity(
		ContentType(MediaTypes.`application/json`, HttpCharsets.`UTF-8`),
		upickle.write(value)))

	def bookmarks(user: User): HttpResponse = jsonResponse(user.bookmarks)

	def collections(implicit ntx: Ntx): HttpResponse = jsonResponse(listCollections.map { c => Narration(c.id, c.name, c.size, Nil) })


	def collection(collection: Collection)(implicit ntx: Ntx) = {
		def assetList: List[Story.Asset] = {
			def innerAssets(node: Node): List[Story.Asset] = {
				Traversal.fold(List[Story.Asset](), node) { state => {
						case Coin.isAsset(asset) => asset.story.copy(blob = asset.blob.map(b => Story.Blob(b.sha1, b.mediatype))) :: state
						case _ => state
					}
				}
			}

			Traversal.next(collection.self).fold[List[Story.Asset]](Nil)(innerAssets).reverse
		}
		jsonResponse(Narration(collection.id, collection.name, collection.size, assetList))
	}

	def stats(stats: Stats)(implicit ntx: Ntx): HttpResponse = jsonResponse{
			val cn = Config.get()
			Map(
				"Downloaded" -> cn.downloaded.toString,
				"Downloads" -> cn.downloads.toString,
				"Compressed Downloads" -> cn.downloadsCompressed.toString,
				"Failed Downloads" -> cn.downloadsFailed.toString,
				"Narrations" -> ntx.nodes(label.Collection).size.toString,
				"Chapters" -> ntx.nodes(label.Chapter).size.toString,
				"Assets" -> ntx.nodes(label.Asset).size.toString,
				"Uptime" -> stats.uptime.toString,
				"Total requests" -> stats.totalRequests.toString,
				"Open requests" -> stats.openRequests.toString,
				"Max open requests" -> stats.maxOpenRequests.toString,
				"Total connections" -> stats.totalConnections.toString,
				"Open connections" -> stats.openConnections.toString,
				"Max open connections" -> stats.maxOpenConnections.toString,
				"Requests timed out" -> stats.requestTimeouts.toString)
		}

}
