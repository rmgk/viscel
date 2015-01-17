package viscel.server

import java.io.File

import akka.actor.{Actor, ActorRefFactory}
import akka.pattern.ask
import spray.can.Http
import spray.can.server.Stats
import spray.http.{ContentType, MediaTypes}
import spray.routing.{HttpService, Route}
import viscel.database.Neo
import viscel.narration.Narrators
import viscel.store.BlobStore.hashToFilename
import viscel.store.{Books, User}
import viscel.{Deeds, Log, ReplUtil, Viscel}

import scala.Predef.{$conforms, ArrowAssoc}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContextExecutor, Future}


class Server(neo: Neo) extends Actor with HttpService {

	implicit def neoIsImplicit: Neo = neo

	def actorRefFactory: ActorRefFactory = context

	val users = new Users

	override def receive: Receive = runRoute {
		//(encodeResponse(Gzip) | encodeResponse(Deflate) | encodeResponse(NoEncoding)) {
		authenticate(users.loginOrCreate) { user => defaultRoute(user) }
		//}
	}

	// we use the enclosing ActorContext's or ActorSystem's dispatcher for our Futures and Scheduler
	implicit def implicitExecutionContext: ExecutionContextExecutor = actorRefFactory.dispatcher


	def handleBookmarksForm(user: User)(continue: User => Route): Route =
		formFields(('narration.?.as[Option[String]], 'bookmark.?.as[Option[Int]])) { (colidOption, bmposOption) =>
			val newUser = for (bmpos <- bmposOption; colid <- colidOption) yield {
				if (bmpos > 0) users.userUpdate(user.setBookmark(colid, bmpos))
				else users.userUpdate(user.removeBookmark(colid))
			}
			continue(newUser.getOrElse(user))
		}

	def defaultRoute(user: User): Route =
		path("") {
			complete(ServerPages.landing)
		} ~
			path("stop") {
				if (!user.isAdmin) reject
				else complete {
					Future {
						spray.util.actorSystem.shutdown()
						Viscel.neo.shutdown()
						Log.info("shutdown complete")
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
				handleBookmarksForm(user)(newUser => complete(ServerPages.bookmarks(newUser)))
			} ~
			path("narrations") {
				complete(ServerPages.jsonResponse(neo.tx(Books.allNarrations(deep = false)(_))))
			} ~
			path("narration" / Segment) { collectionId =>
				rejectNone(neo.tx { Books.getNarration(collectionId, deep = true)(_) }) { narration =>
					complete(ServerPages.jsonResponse(narration))
				}
			} ~
			pathPrefix("blob" / Segment) { (sha1) =>
				val filename = new File(hashToFilename(sha1))
				pathEnd { getFromFile(filename) } ~
					path(Segment / Segment) { (part1, part2) =>
						getFromFile(filename, ContentType(MediaTypes.getForKey(part1 -> part2).get))
					}
			} ~
			pathPrefix("hint") {
				path("narrator" / Segment) { narratorID =>
					rejectNone(Narrators.get(narratorID)) { nar =>
						parameters('force.?.as[Option[Boolean]]) { force =>
							complete {
								Deeds.narratorHint(nar, force.getOrElse(false))
								force.toString
							}
						}
					}
				}
			} ~
			path("stats") {
				complete {
					val stats = actorRefFactory.actorSelection("/user/IO-HTTP/listener-0")
						.ask(Http.GetStats)(1.second)
						.mapTo[Stats]
					stats.map { s => neo.tx { ServerPages.stats(s)(_) } }
				}
			} ~
			path("export" / Segment) { (id) =>
				complete(try { ReplUtil.export(id)(Viscel.neo); "success" }
				catch { case e: Exception => e.toString })
			} ~
			path("add") {
				complete("not yet implemented")
			}

	def rejectNone[T](opt: => Option[T])(route: T => Route) = opt.map { route }.getOrElse(reject)
}
