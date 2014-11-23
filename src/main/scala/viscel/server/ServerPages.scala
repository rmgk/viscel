package viscel.server

import org.neo4j.graphdb.Node
import org.neo4j.tooling.GlobalGraphOperations
import spray.can.server.Stats
import spray.http._
import upickle.Writer
import viscel.Deeds
import viscel.database.{Ntx, Traversal, label}
import viscel.shared.JsonCodecs.stringMapW
import viscel.shared.Story
import viscel.shared.Story.Narration
import viscel.store.{Coin, Collection, Config, User}
import scala.collection.JavaConverters.iterableAsScalaIterableConverter

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

	def collections(implicit ntx: Ntx): HttpResponse = {
		val allCollections = GlobalGraphOperations.at(ntx.db).getAllNodesWithLabel(label.Collection).asScala.map { Collection.apply }
		jsonResponse(allCollections.map { _.narration(nested = false)})
	}


	def collection(collection: Collection)(implicit ntx: Ntx) = {	jsonResponse(collection.narration(nested = true)) }

	def stats(stats: Stats)(implicit ntx: Ntx): HttpResponse = jsonResponse{
			val cn = Config.get()
			Map[String, String](
				"Downloaded" -> cn.downloaded.toString,
				"Downloads" -> cn.downloads.toString,
				"Compressed downloads" -> cn.downloadsCompressed.toString,
				"Failed downloads" -> cn.downloadsFailed.toString,
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
				"Requests timed out" -> stats.requestTimeouts.toString,
				"Session ui requests" -> Deeds.sessionUiRequests.get.toString)
		}

}
