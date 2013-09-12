package viscel.server

import akka.actor.{ ActorSystem, Props, Actor }
import akka.pattern.ask
import com.typesafe.scalalogging.slf4j.Logging
import java.io.File
import scala.concurrent.duration._
import scala.concurrent.future
import scala.concurrent.Future
import spray.can.Http
import spray.can.server.Stats
import spray.http.{ MediaTypes, ContentType, FormData }
import spray.httpx.encoding.{ Gzip, Deflate, NoEncoding }
import spray.routing.authentication._
import spray.routing.directives.ContentTypeResolver
import spray.routing.{ HttpService, RequestContext, Route }
import viscel.store.CollectionNode
import viscel.store.ElementNode
import viscel.store.ConfigNode
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
	def receive = runRoute {
		//(encodeResponse(Gzip) | encodeResponse(Deflate) | encodeResponse(NoEncoding)) {
		authenticate(loginOrCreate) { user => handleFormFields(user) }
		//}
	}

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
						logger.warn(s"create new user $user $password")
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

	def handleFormFields(user: UserNode) =
		formFields('bookmark.?.as[Option[Long]], 'remove_bookmark.?.as[Option[Long]]) { (bm, remove) =>
			bm.foreach { bid => user.setBookmark(ElementNode(bid)) }
			remove.foreach { colid => user.deleteBookmark(CollectionNode(colid)) }
			defaultRoute(user)
		}

	def defaultRoute(user: UserNode) =
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
					complete(FrontPage(user, cn))
				}
			} ~
			path("c" / Segment) { col =>
				rejectNone(CollectionNode(col)) { cn =>
					complete(ChapterPage(user, cn))
				}
			} ~
			path("v" / Segment / IntNumber / IntNumber) { (col, chapter, pos) =>
				rejectNone(CollectionNode(col)) { cn =>
					complete(viewFallback(user, cn, chapter, pos))
				}
			} ~
			path("i" / IntNumber) { id =>
				complete(PageDispatcher(user, ViscelNode(id).get))
			} ~
			path("r" / IntNumber) { id =>
				complete(RawPage(user, ViscelNode(id).get))
			} ~
			(path("s") & parameter('q)) { query =>
				complete(SearchPage(user, query))
			} ~
			path("stats") {
				complete {
					val stats = actorRefFactory.actorSelection("/user/IO-HTTP/listener-0")
						.ask(Http.GetStats)(1.second)
						.mapTo[Stats]
					stats.map { StatsPage(user, _) }
				}
			} ~
			path("select") {
				entity(as[FormData]) { form =>
					if (form.fields.get("select_cores") == Some("apply")) {
						val applied = form.fields.collect { case (col, "select") => col }.toSeq
						logger.info(s"selecting $applied")
						ConfigNode().legacyCollections = applied
					}
					complete { SelectionPage(user) }
				}
			}

	def rejectNone[T](opt: Option[T])(route: T => Route) = opt.map { route(_) }.getOrElse(reject)

	//def viewFallback(user: UserNode, cn: CollectionNode, chapter: Int) = cn(chapter).map { ChapterPage(user, _) }.getOrElse { FrontPage(user, cn) }
	def viewFallback(user: UserNode, cn: CollectionNode, chapter: Int, pos: Int) = cn(chapter).flatMap { _(pos).map { ViewPage(user, _) } }.getOrElse { FrontPage(user, cn) }

}
