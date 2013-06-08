package viscel

import akka.actor.{ActorSystem, Props, Actor}
import spray.routing.{HttpService, RequestContext}
import spray.http.{MediaTypes, ContentType}
import spray.routing.directives.ContentTypeResolver
import viscel.collection.Legacy

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

trait DefaultRoutes extends HttpService {

	// we use the enclosing ActorContext's or ActorSystem's dispatcher for our Futures and Scheduler
	implicit def executionContext = actorRefFactory.dispatcher

	def hashToFilename(h: String): String = (new StringBuilder(h)).insert(2,'/').insert(0,"../../viscel/cache/").toString

	val blobs = new collection.Blobs

	implicit val typeResolver = new ContentTypeResolver {
		def apply(fileName: String): ContentType = ContentType(MediaTypes.`image/jpeg`)
	}

	val defaultRoute = {
		path("") {
			complete("index")
		} ~
		path("stop") {
			complete {
				spray.util.actorSystem.shutdown()
				"shutdown"
			}
		} ~
		path("b")(complete(blobs.display)) ~
		path("b" / Segment) { hash =>
			val filename = hashToFilename(hash)
			getFromFile(filename)
		} ~
		pathPrefix("legacy" / Segment) {col =>
			Legacy(col).route
		} ~
		pathPrefix("experiment") {
			Viscel.cW.collection.route
		}
	}

}
