package viscel.server

import akka.actor.{ ActorSystem, Props, Actor }
import spray.routing.{ HttpService, RequestContext }
import spray.http.{ MediaTypes, ContentType }
import spray.routing.directives.ContentTypeResolver
import viscel.display._
import java.io.File
import viscel.store.CollectionNode
import viscel.store.ElementNode
import viscel.time
import com.typesafe.scalalogging.slf4j.Logging
import scala.concurrent.future
import spray.routing.authentication._
import scala.concurrent.Future

// we don't implement our route structure directly in the service actor because
// we want to be able to test it independently, without having to spin up an actor
class Server extends Actor with DefaultRoutes {

	// the HttpService trait defines only one abstract member, which
	// connects the services environment to the enclosing actor or test
	def actorRefFactory = context

	// this actor only runs our route, but you could add
	// other things here, like request stream processing,
	// timeout handling or alternative handler registration
	def receive = runRoute(defaultRoute)
}

trait DefaultRoutes extends HttpService with Logging {

	// we use the enclosing ActorContext's or ActorSystem's dispatcher for our Futures and Scheduler
	implicit def executionContext = actorRefFactory.dispatcher

	def hashToFilename(h: String): String = (new StringBuilder(h)).insert(2, '/').insert(0, "../cache/").toString

	val defaultRoute = {
		(path("") | path("index")) {
			complete(time("total") { IndexPage() })
		} ~
			path("stop") {
				complete {
					spray.util.actorSystem.shutdown()
					viscel.store.Neo.shutdown()
					"shutdown"
				}
			} ~
			path("css") {
				getFromFile("../style.css")
			} ~
			path("b" / Segment) { hash =>
				val filename = hashToFilename(hash)
				getFromFile(new File(filename), ContentType(MediaTypes.`image/jpeg`))
			} ~
			pathPrefix("f" / Segment) { col =>
				formFields('bookmark.?.as[Option[Int]], 'submit.?.as[Option[String]]) { (bm, remove) =>
					val cn = time(s"create collection node for $col") { CollectionNode(col) }
					bm.foreach { cn.bookmark(_) }
					remove.foreach { case "remove" => cn.bookmarkDelete(); case _ => }
					complete(time("total") { FrontPage(cn) })
				}
			} ~
			pathPrefix("v" / Segment / IntNumber) { (col, pos) =>
				complete(time("total") { viewFallback(col, pos.toInt) })
			} ~
			pathPrefix("id" / IntNumber) { id =>
				complete(time("total") { ViewPage(time(s"create element node for $id") { ElementNode(id) }) })
			} ~
			path("importLegacy") {
				complete {
					future { viscel.store.LegacyImporter.importAll() }
					"will work on this"
				}
			}
	}

	def viewFallback(col: String, pos: Int) = time(s"create element node for $col $pos") { time(s"create collection node for $col") { CollectionNode(col) }(pos) }.map { ViewPage(_) }
		.getOrElse(FrontPage(time(s"create collection node for $col") { CollectionNode(col) }))

}
