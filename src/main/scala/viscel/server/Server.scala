package viscel.server

import java.io.File

import akka.actor.{Actor, ActorRefFactory}
import akka.pattern.ask
import com.typesafe.scalalogging.slf4j.StrictLogging
import org.scalactic.TypeCheckedTripleEquals._
import org.scalactic.{Bad, Good}
import spray.can.Http
import spray.can.server.Stats
import spray.http.{ContentType, MediaTypes}
import spray.routing.authentication.{BasicAuth, UserPass, UserPassAuthenticator}
import spray.routing.{HttpService, Route}
import viscel.database.{Neo, NeoSingleton}
import viscel.narration.Narrator
import viscel.store._

import scala.Predef.{any2ArrowAssoc, conforms}
import scala.collection.immutable.Map
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}


class Server(neo: Neo) extends Actor with HttpService with StrictLogging {

	implicit def neoIsImplicit: Neo = neo

	def actorRefFactory: ActorRefFactory = context

	override def receive: Receive = runRoute {
		//(encodeResponse(Gzip) | encodeResponse(Deflate) | encodeResponse(NoEncoding)) {
		authenticate(loginOrCreate) { user => handleFormFields(user) }
		//}
	}

	// we use the enclosing ActorContext's or ActorSystem's dispatcher for our Futures and Scheduler
	implicit def implicitExecutionContext: ExecutionContextExecutor = actorRefFactory.dispatcher

	var userCache = Map[String, User]()

	def userUpdate(user: User): Unit = {
		userCache += user.id -> user
		User.store(user)
	}

	def getUserNode(name: String, password: String): User = {
		userCache.getOrElse(name, {
			val user = User.load(name) match {
				case Good(g) => g
				case Bad(e) =>
					logger.warn(s"could not open user $name: $e")
					User(name, password, Map())
			}
			userCache += name -> user
			user
		})
	}

	val loginOrCreate = BasicAuth(UserPassAuthenticator[User] {
		case Some(UserPass(user, password)) =>
			logger.trace(s"login: $user $password")
			// time("login") {
			if (user.matches("\\w+")) {
				Future.successful { Some(getUserNode(user, password)).filter(_.password === password) }
			}
			else { Future.successful(None) }
		// }
		case None =>
			Future.successful(None)
	}, "Username is used to store configuration; Passwords are saved in plain text; User is created on first login")

	def handleFormFields(user: User) =
		formFields(('narration.?.as[Option[String]], 'bookmark.?.as[Option[Int]])) { (colidOption, bmposOption) =>
			for (bmpos <- bmposOption; colid <- colidOption) {
				if (bmpos > 0) userUpdate(user.setBookmark(colid, bmpos))
				else userUpdate(user.removeBookmark(colid))
			}
			defaultRoute(user)
		}

	def defaultRoute(user: User): Route =
		path("") {
			complete(ServerPages.landing)
		} ~
			path("stop") {
				complete {
					Future {
						spray.util.actorSystem.shutdown()
						NeoSingleton.shutdown()
					}
					"shutdown"
				}
			} ~
			path("css") {
				getFromResource("style.css")
			} ~
			path("js") {
				getFromFile("js/target/scala-2.10/viscel-js-opt.js") ~ getFromFile("js/target/scala-2.10/viscel-js-fastopt.js") ~
				getFromResource("viscel-js-opt.js") ~ getFromResource("viscel-js-fastopt.js")
			} ~
			path ("viscel-js-fastopt.js.map") {
				getFromFile("js/target/scala-2.10/viscel-js-fastopt.js.map")
			}~
			path ("viscel-js-opt.js.map") {
				getFromFile("js/target/scala-2.10/viscel-js-opt.js.map")
			} ~
			path("bookmarks") {
				complete(ServerPages.bookmarks(user))
			} ~
			path("narrations") {
				complete(neo.tx(ServerPages.collections(_)))
			} ~
			path("narration"/ Segment) { collectionId =>
				rejectNone(Narrator.get(collectionId)) { core =>
					val collection = neo.tx { Collection.findAndUpdate(core)(_) }
					complete(neo.tx{ServerPages.collection(collection)(_)})
				}
			} ~
			pathPrefix("blob" / Segment) { (sha1) =>
				val filename = viscel.hashToFilename(sha1)
				pathEnd { getFromFile(new File(filename)) } ~
				path(Segment / Segment) { (part1, part2) =>
					getFromFile(new File(filename), ContentType(MediaTypes.getForKey(part1 -> part2).get))
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
