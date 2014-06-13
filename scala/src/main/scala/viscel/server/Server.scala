package viscel.server

import akka.actor.Actor
import com.typesafe.scalalogging.slf4j.StrictLogging
import scala.concurrent.Future
import scala.concurrent.duration._
import spray.can.Http
import spray.can.server.Stats
import spray.http.{ MediaTypes, ContentType }
import spray.routing.authentication._
import spray.routing.{ HttpService, Route }
import viscel.store.CollectionNode
import viscel.store.ElementNode
import viscel.store.UserNode
import viscel.store.ViscelNode
import java.io.File
import akka.pattern.ask
import org.scalactic.TypeCheckedTripleEquals._

// we don't implement our route structure directly in the service actor because
// we want to be able to test it independently, without having to spin up an actor
class Server extends Actor with DefaultRoutes with StrictLogging {

	// the HttpService trait defines only one abstract member, which
	// connects the services environment to the enclosing actor or test
	def actorRefFactory = context

	// this actor only runs our route, but you could add
	// other things here, like request stream processing,
	// timeout handling or alternative handler registration
	override def receive = runRoute {
		//(encodeResponse(Gzip) | encodeResponse(Deflate) | encodeResponse(NoEncoding)) {
		authenticate(loginOrCreate) { user => handleFormFields(user) }
		//}
	}

}

trait DefaultRoutes extends HttpService {
	this: Server =>

	// we use the enclosing ActorContext's or ActorSystem's dispatcher for our Futures and Scheduler
	implicit def executionContext = actorRefFactory.dispatcher

	def hashToFilename(h: String): String = new StringBuilder(h).insert(2, '/').insert(0, "../cache/").toString()

	var userCache = Map[String, UserNode]()

	def getUserNode(name: String, password: String) =
		userCache.getOrElse(name, {
			UserNode(name).getOrElse {
				logger.warn(s"create new user $name $password")
				UserNode.create(name, password)
			}.tap(user => userCache += name -> user)
		})

	val loginOrCreate = BasicAuth(UserPassAuthenticator[UserNode] {
		case Some(UserPass(user, password)) =>
			logger.trace(s"login: $user $password")
			// time("login") {
			if (user.matches("\\w+")) {
				Future.successful {
					Some(getUserNode(user, password)).filter(_.password === password)
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
					Future {
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
			//			path("c" / Segment) { col =>
			//				rejectNone(CollectionNode(col)) { cn =>
			//					complete(ChapterPage(user, cn))
			//				}
			//			} ~
			path("v" / Segment / IntNumber) { (col, pos) =>
				rejectNone(CollectionNode(col)) { cn =>
					rejectNone(cn(pos)) { en =>
						complete(ViewPage(user, en))
					}
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
			}
	//			path("select") {
	//				entity(as[FormData]) { form =>
	//					if (form.fields.contains(("select_cores", "apply"))) {
	//						val applied = form.fields.collect { case (col, "select") => col }.toSeq
	//						val config = ConfigNode()
	//						logger.info(s"selecting $applied")
	//						val before = config.legacyCollections
	//						config.legacyCollections = applied
	//						val added: Set[String] = applied.toSet -- before
	//						logger.info(s"adding $added")
	//						added.foreach(id => context.actorSelection("/user/clockwork") ! viscel.core.Clockwork.Enqueue(id))
	//					}
	//					complete { SelectionPage(user) }
	//				}
	//			}

	def rejectNone[T](opt: => Option[T])(route: T => Route) = opt.map { route }.getOrElse(reject)
}
