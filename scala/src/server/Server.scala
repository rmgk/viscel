package viscel.server

import akka.actor.{ ActorSystem, Props, Actor }
import com.typesafe.scalalogging.slf4j.Logging
import java.io.File
import scala.concurrent.future
import scala.concurrent.Future
import spray.http.{ MediaTypes, ContentType }
import spray.routing.authentication._
import spray.routing.directives.ContentTypeResolver
import spray.routing.{ HttpService, RequestContext, Route }
import viscel.store.CollectionNode
import viscel.store.ElementNode
import viscel.store.UserNode
import viscel.store.ViscelNode
import viscel.time

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

	val loginOrCreate = BasicAuth(UserPassAuthenticator[UserNode] {
		case Some(UserPass(user, password)) =>
			logger.trace(s"login: $user $password")
			// time("login") {
			if (user.matches("\\w+")) {
				Future.successful {
					UserNode(user).orElse {
						logger.warn("create new user $user $password")
						Some(UserNode.create(user, password))
					}.flatMap { un =>
						if (un.password == password) Some(un)
						else None
					}
				}
			}
			else { Future.successful(None) }
		// }
		case None =>
			Future.successful(None)
	}, "Username is used to store configuration; Passwords are saved in plain text; User is created on first login")

	val defaultRoute =
		authenticate(loginOrCreate) { user =>
			(path("") | path("index")) {
				complete(IndexPage(user))
			} ~
				path("stop") {
					complete {
						future {
							spray.util.actorSystem.shutdown()
							viscel.store.Neo.shutdown()
						}
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
				path("f" / Segment) { col =>
					rejectNone(CollectionNode(col)) { cn =>
						formFields('bookmark.?.as[Option[Long]], 'submit.?.as[Option[String]]) { (bm, remove) =>
							bm.foreach { bid => user.setBookmark(ElementNode(bid)) }
							remove.foreach { case "remove" => user.deleteBookmark(cn); case _ => }
							complete(time("total") { FrontPage(user, cn) })
						}
					}
				} ~
				path("v" / Segment / IntNumber) { (col, pos) =>
					rejectNone(CollectionNode(col)) { cn =>
						complete(viewFallback(user, cn, pos))
					}
				} ~
				path("id" / IntNumber) { id =>
					complete(PageDispatcher(user, ViscelNode(id).get))
				} ~
				(path("s") & parameter('q)) { query =>
					complete(SearchPage(user, query))
				}
		}

	def rejectNone[T](opt: Option[T])(route: T => Route) = opt.map { route(_) }.getOrElse(reject)

	def viewFallback(user: UserNode, cn: CollectionNode, pos: Int) = cn(pos).map { ViewPage(user, _) }.getOrElse { FrontPage(user, cn) }

}
