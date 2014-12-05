package viscel.server

import java.io.File

import akka.actor.{Actor, ActorRefFactory}
import akka.pattern.ask
import spray.can.Http
import spray.can.server.Stats
import spray.http.{ContentType, MediaTypes}
import spray.routing.{HttpService, Route}
import viscel.Viscel
import viscel.database.Neo
import viscel.narration.Narrator
import viscel.store.BlobStore.hashToFilename
import viscel.store.{Collection, User}

import scala.Predef.{$conforms, ArrowAssoc}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContextExecutor, Future}


class Server(neo: Neo) extends Actor with HttpService {

	implicit def neoIsImplicit: Neo = neo

	def actorRefFactory: ActorRefFactory = context

	val users = new Users

	override def receive: Receive = runRoute {
		//(encodeResponse(Gzip) | encodeResponse(Deflate) | encodeResponse(NoEncoding)) {
		authenticate(users.loginOrCreate) { user => handleFormFields(user) }
		//}
	}

	// we use the enclosing ActorContext's or ActorSystem's dispatcher for our Futures and Scheduler
	implicit def implicitExecutionContext: ExecutionContextExecutor = actorRefFactory.dispatcher


	def handleFormFields(user: User) =
		formFields(('narration.?.as[Option[String]], 'bookmark.?.as[Option[Int]])) { (colidOption, bmposOption) =>
			val newUser = for (bmpos <- bmposOption; colid <- colidOption) yield {
				if (bmpos > 0) users.userUpdate(user.setBookmark(colid, bmpos))
				else users.userUpdate(user.removeBookmark(colid))
			}
			defaultRoute(newUser.getOrElse(user))
		}

	def defaultRoute(user: User): Route =
		path("") {
			complete(ServerPages.landing)
		} ~
			path("stop") {
				complete {
					Future {
						spray.util.actorSystem.shutdown()
						Viscel.neo.shutdown()
					}
					"shutdown"
				}
			} ~
			path("css") {
				getFromResource("style.css")
			} ~
			path("js") {
				getFromFile("js/target/scala-2.11/viscel-js-opt.js") ~ getFromFile("js/target/scala-2.11/viscel-js-fastopt.js") ~
					getFromResource("viscel-js-opt.js") ~ getFromResource("viscel-js-fastopt.js")
			} ~
			path("viscel-js-fastopt.js.map") {
				getFromFile("js/target/scala-2.11/viscel-js-fastopt.js.map")
			} ~
			path("viscel-js-opt.js.map") {
				getFromFile("js/target/scala-2.11/viscel-js-opt.js.map")
			} ~
			path("bookmarks") {
				complete(ServerPages.bookmarks(user))
			} ~
			path("narrations") {
				complete(neo.tx(ServerPages.collections(_)))
			} ~
			path("narration" / Segment) { collectionId =>
				rejectNone(Narrator.get(collectionId)) { core =>
					val collection = neo.tx { Collection.findAndUpdate(core)(_) }
					complete(neo.tx { ServerPages.collection(collection)(_) })
				}
			} ~
			pathPrefix("blob" / Segment) { (sha1) =>
				val filename = new File(hashToFilename(sha1))
				pathEnd { getFromFile(filename) } ~
					path(Segment / Segment) { (part1, part2) =>
						getFromFile(filename, ContentType(MediaTypes.getForKey(part1 -> part2).get))
					}
			} ~
			path("stats") {
				complete {
					val stats = actorRefFactory.actorSelection("/user/IO-HTTP/listener-0")
						.ask(Http.GetStats)(1.second)
						.mapTo[Stats]
					stats.map { s => neo.tx { ServerPages.stats(s)(_) } }
				}
			}

	def rejectNone[T](opt: => Option[T])(route: T => Route) = opt.map { route }.getOrElse(reject)
}
