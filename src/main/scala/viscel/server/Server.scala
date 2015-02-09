package viscel.server

import akka.actor.{Actor, ActorRefFactory}
import akka.pattern.ask
import spray.can.Http
import spray.can.server.Stats
import spray.http.{ContentType, MediaTypes}
import spray.routing.{HttpService, Route}
import viscel.database.{Books, Neo}
import viscel.narration.{Metarrators, Narrators}
import viscel.store.BlobStore.hashToPath
import viscel.store.User
import viscel.{Deeds, Log, ReplUtil, Viscel}

import scala.Predef.{$conforms, ArrowAssoc}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}


class Server(neo: Neo) extends Actor with HttpService {

	implicit def neoIsImplicit: Neo = neo

	def actorRefFactory: ActorRefFactory = context

	val users = new UserStore

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
				complete(ServerPages.jsonResponse(neo.tx(Books.allDescriptions()(_))))
			} ~
			path("narration" / Segment) { collectionId =>
				rejectNone(neo.tx { ntx => Books.find(collectionId)(ntx).map(_.content()(ntx)) }) { content =>
					complete(ServerPages.jsonResponse(content))
				}
			} ~
			pathPrefix("blob" / Segment) { (sha1) =>
				val filename = hashToPath(sha1).toFile
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
				if (!user.isAdmin) reject
				else onComplete(Future(ReplUtil.export(id)(Viscel.neo))) {
					case Success(v) => complete("success")
					case Failure(e) => complete(e.toString())
				}
			} ~
			path("import" / Segment) { (id) =>
				if (!user.isAdmin) reject
				else parameters('name.as[String], 'path.as[String]) { (name, path) =>
					onComplete(Future(ReplUtil.importFolder(path, s"Import_$id", name)(Viscel.neo))) {
						case Success(v) => complete("success")
						case Failure(e) => complete(e.toString())
					}
				}
			} ~
			path("add") {
				if (!user.isAdmin) reject
				else parameter('url.as[String]) { url =>
					onComplete(Metarrators.add(url, Viscel.iopipe)) {
						case Success(v) => complete(s"found ${ v.map(_.id) }")
						case Failure(e) => complete { e.getMessage }
					}
				}
			} ~
			path("reload") {
				if (!user.isAdmin) reject
				else complete {
					Narrators.update()
					"done"
				}
			}

	def rejectNone[T](opt: => Option[T])(route: T => Route) = opt.map { route }.getOrElse(reject)
}
