package viscel.server

import org.neo4j.tooling.GlobalGraphOperations
import rescala.propagation.Engines.default
import spray.can.server.Stats
import spray.http._
import upickle.Writer
import viscel.Deeds
import viscel.database.{Ntx, label}
import viscel.narration.Narrator
import viscel.shared.JsonCodecs.stringMapW
import viscel.store.{Collection, Config, User}

import scala.Predef.ArrowAssoc
import scala.collection.JavaConverters.iterableAsScalaIterableConverter
import scala.collection.immutable.Map
import scalatags.Text.attrs.{`type`, href, rel, src, title}
import scalatags.Text.implicits.{stringAttr, stringFrag}
import scalatags.Text.tags.{body, head, html, link, script}
import scalatags.Text.{RawFrag, Tag}


object ServerPages {
	val path_css: String = "/css"
	val path_js: String = "/js"

	val fullHtml: Tag =
		html(
			head(
				link(href := path_css, rel := "stylesheet", `type` := MediaTypes.`text/css`.toString()),
				title := "Viscel"),
			body("if nothing happens, your javascript does not work"),
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

		val allCollections = GlobalGraphOperations.at(ntx.db).getAllNodesWithLabel(label.Collection).asScala.map { Collection.apply } ++
			Narrator.availableCores.map(Collection.findAndUpdate)
		jsonResponse(allCollections.map { _.narration(deep = false) })
	}


	def collection(collection: Collection)(implicit ntx: Ntx) = { jsonResponse(collection.narration(deep = true)) }

	def stats(stats: Stats)(implicit ntx: Ntx): HttpResponse = jsonResponse {
		val cn = Config.get()
		Map[String, Long](
			"Downloaded" -> cn.downloaded,
			"Downloads" -> cn.downloads,
			"Compressed downloads" -> cn.downloadsCompressed,
			"Failed downloads" -> cn.downloadsFailed,
			"Narrations" -> ntx.nodes(label.Collection).size,
			"Chapters" -> ntx.nodes(label.Chapter).size,
			"Assets" -> ntx.nodes(label.Asset).size,
			"Uptime" -> stats.uptime.toSeconds,
			"Total requests" -> stats.totalRequests,
			"Open requests" -> stats.openRequests,
			"Max open requests" -> stats.maxOpenRequests,
			"Total connections" -> stats.totalConnections,
			"Open connections" -> stats.openConnections,
			"Max open connections" -> stats.maxOpenConnections,
			"Requests timed out" -> stats.requestTimeouts,
			"Session ui requests" -> Deeds.sessionUiRequests.now)
	}

}
