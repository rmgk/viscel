package viscel.server

import java.io.File

import akka.actor.{Actor, ActorRefFactory}
import akka.pattern.ask
import com.typesafe.scalalogging.slf4j.StrictLogging
import org.scalactic.Good
import org.scalactic.TypeCheckedTripleEquals._
import spray.can.Http
import spray.can.server.Stats
import spray.http.ContentType
import spray.routing.authentication.{BasicAuth, UserPass, UserPassAuthenticator}
import spray.routing.{HttpService, Route}
import viscel.cores.Core
import viscel.crawler.Clockwork
import viscel.server.pages._
import viscel.store._
import viscel.store.nodes._

import scala.Predef.{any2ArrowAssoc, conforms}
import scala.collection.immutable.Map
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}


class Server extends Actor with HttpService with StrictLogging {

	implicit val neo = Neo

	def actorRefFactory: ActorRefFactory = context

	override def receive: Receive = runRoute {
		//(encodeResponse(Gzip) | encodeResponse(Deflate) | encodeResponse(NoEncoding)) {
		authenticate(loginOrCreate) { user => handleFormFields(user) }
		//}
	}

	// we use the enclosing ActorContext's or ActorSystem's dispatcher for our Futures and Scheduler
	implicit def implicitExecutionContext: ExecutionContextExecutor = actorRefFactory.dispatcher

	var userCache = Map[String, UserNode]()

	def getUserNode(name: String, password: String): UserNode = {
		userCache.getOrElse(name, {
			val user = Nodes.find.user(name).getOrElse {
				logger.warn(s"create new user $name $password")
				Nodes.create.user(name, password)
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
			bm.foreach { bid =>
				Nodes.byID(bid) match {
					case Good(asset@AssetNode(_)) => user.setBookmark(asset)
					case other => logger.warn(s"not an asset: $other")
				}
			}
			remove.foreach { colid =>
				Nodes.byID(colid) match {
					case Good(col@CollectionNode(_)) => user.deleteBookmark(col)
					case other => logger.warn(s"not a collection: $other")
				}
			}
			defaultRoute(user)
		}

	def defaultRoute(user: UserNode) =
		(path("") | path("index")) {
			complete(Pages.index(user))
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
				neo.txs {
					val blob = Nodes.byID(nid).get.asInstanceOf[BlobNode]
					val filename = viscel.hashToFilename(blob.sha1)
					getFromFile(new File(filename), ContentType(blob.mediatype))
				}
			} ~
			path("f" / Segment) { collectionId =>
				rejectNone(Core.get(collectionId)) { core =>
					val collection = Core.getCollection(core)
					Clockwork.collectionHint(collection)
					complete(Pages.front(user, collection))
				}
			} ~
			//			path("c" / Segment) { col =>
			//				rejectNone(CollectionNode(col)) { cn =>
			//					complete(ChapterPage(user, cn))
			//				}
			//			} ~
			path("v" / Segment / IntNumber) { (col, pos) =>
				neo.txs {
					rejectNone(Nodes.find.collection(col)) { cn =>
						rejectNone(cn(pos)) { en =>
							Clockwork.archiveHint(en)
							complete(Pages.view(user, en))
						}
					}
				}
			} ~
			path("i" / LongNumber) { id =>
				neo.txs {
					val node = Nodes.byID(id).get
					node match {
						case archiveNode: ArchiveNode => Clockwork.archiveHint(archiveNode)
						case collectionNode: CollectionNode => Clockwork.collectionHint(collectionNode)
					}
					complete(Pages(user, node))
				}
			} ~
			path("r" / LongNumber) { id =>
				complete(Pages.raw(user, Nodes.byID(id).get))
			} ~
			(path("s") & parameter('q)) { query =>
				complete(Pages.search(user, query))
			} ~
			path("stats") {
				complete {
					val stats = actorRefFactory.actorSelection("/user/IO-HTTP/listener-0")
						.ask(Http.GetStats)(1.second)
						.mapTo[Stats]
					stats.map { Pages.stats(user, _) }
				}
			} ~
			path("core" / Segment) { coreId =>
				val core = Core.get(coreId).foreach { core =>
					//actorRefFactory.actorSelection("/user/clockwork") ! Messages.Run(core)
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
