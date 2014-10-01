package viscel.server

import java.io.File

import akka.actor.{ActorRefFactory, Actor}
import akka.pattern.ask
import com.typesafe.scalalogging.slf4j.StrictLogging
import spray.can.Http
import spray.can.server.Stats
import spray.http.ContentType
import spray.routing.authentication.{BasicAuth, UserPassAuthenticator, UserPass}
import spray.routing.{HttpService, Route}
import viscel.core.{Core, Messages}
import org.scalactic.TypeCheckedTripleEquals._
import viscel.store._

import scala.Predef.{any2ArrowAssoc, conforms}
import scala.collection.immutable.Map
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration._

// we don't implement our route structure directly in the service actor because
// we want to be able to test it independently, without having to spin up an actor
class Server extends Actor with DefaultRoutes with StrictLogging {

	// the HttpService trait defines only one abstract member, which
	// connects the services environment to the enclosing actor or test
	def actorRefFactory: ActorRefFactory = context

	// this actor only runs our route, but you could add
	// other things here, like request stream processing,
	// timeout handling or alternative handler registration
	override def receive: Receive = runRoute {
		//(encodeResponse(Gzip) | encodeResponse(Deflate) | encodeResponse(NoEncoding)) {
		authenticate(loginOrCreate) { user => handleFormFields(user) }
		//}
	}

}

trait DefaultRoutes extends HttpService {
	this: Server =>

	// we use the enclosing ActorContext's or ActorSystem's dispatcher for our Futures and Scheduler
	implicit def executionContext: ExecutionContextExecutor = actorRefFactory.dispatcher

	var userCache = Map[String, UserNode]()

	def getUserNode(name: String, password: String): UserNode = {
		userCache.getOrElse(name, {
			val user = UserNode(name).getOrElse {
				logger.warn(s"create new user $name $password")
				UserNode.create(name, password)
			}
			userCache += name -> user
			user
		})
	}

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
			bm.foreach { bid => user.setBookmark(AssetNode(bid)) }
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
				getFromResource("style.css")
			} ~
			//			path("b" / Segment) { hash =>
			//				val filename = viscel.hashToFilename(hash)
			//				getFromFile(new File(filename), ContentType(MediaTypes.`image/jpeg`))
			path("b" / LongNumber) { nid =>
				val blob = BlobNode(nid)
				val filename = viscel.hashToFilename(blob.sha1)
				getFromFile(new File(filename), ContentType(blob.mediatype))
			} ~
			path("f" / Segment) { collectionId =>
				rejectNone(Core.get(collectionId)) { core =>
					val collection = Core.getCollection(core)
					actorRefFactory.actorSelection("/user/clockwork") ! Messages.CollectionHint(collection)
					complete(FrontPage(user, collection))
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
						actorRefFactory.actorSelection("/user/clockwork") ! Messages.ArchiveHint(en)
						complete(ViewPage(user, en))
					}
				}
			} ~
			path("i" / LongNumber) { id =>
				val node = ViscelNode(id).get
				node match {
					case archiveNode: ArchiveNode => actorRefFactory.actorSelection("/user/clockwork") ! Messages.ArchiveHint(archiveNode)
					case collectionNode: CollectionNode => actorRefFactory.actorSelection("/user/clockwork") ! Messages.CollectionHint(collectionNode)
				}
				complete(PageDispatcher(user, node))
			} ~
			path("r" / LongNumber) { id =>
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
			path("core" / Segment) { coreId =>
				val core = Core.get(coreId).foreach { core =>
					actorRefFactory.actorSelection("/user/clockwork") ! Messages.Run(core)
				}
				complete(coreId)
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
