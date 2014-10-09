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
import viscel.narration.Narrator
import viscel.crawler.Clockwork
import viscel.server.pages._
import viscel.store._
import viscel.store.coin._

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

	var userCache = Map[String, User]()

	def getUserNode(name: String, password: String): User = {
		userCache.getOrElse(name, {
			val user = Vault.find.user(name).getOrElse {
				logger.warn(s"create new user $name $password")
				Vault.create.user(name, password)
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
				Future.successful {
					Some(getUserNode(user, password)).filter(_.password === password)
				}
			}
			else { Future.successful(None) }
		// }
		case None =>
			Future.successful(None)
	}, "Username is used to store configuration; Passwords are saved in plain text; User is created on first login")

	def handleFormFields(user: User) =
		formFields('bookmark.?.as[Option[Long]], 'remove_bookmark.?.as[Option[Long]]) { (bm, remove) =>
			bm.foreach { bid =>
				Vault.byID(bid) match {
					case Good(asset@Asset(_)) => user.setBookmark(asset)
					case other => logger.warn(s"not an asset: $other")
				}
			}
			remove.foreach { colid =>
				Vault.byID(colid) match {
					case Good(col@Collection(_)) => user.deleteBookmark(col)
					case other => logger.warn(s"not a collection: $other")
				}
			}
			defaultRoute(user)
		}

	def defaultRoute(user: User) =
		(path("") | path("index")) {
			complete(Pages.index(user))
		} ~
			path("stop") {
				Future {
					spray.util.actorSystem.shutdown()
					viscel.store.Neo.shutdown()
				}
				complete { "shutdown" }
			} ~
			path("css") {
				getFromResource("style.css")
			} ~
			path("b" / LongNumber) { nid =>
				neo.txs {
					val blob = Vault.byID(nid).get.asInstanceOf[Blob]
					val filename = viscel.hashToFilename(blob.sha1)
					getFromFile(new File(filename), ContentType(blob.mediatype))
				}
			} ~
			path("f" / Segment) { collectionId =>
				rejectNone(Narrator.get(collectionId)) { core =>
					val collection = Vault.update.collection(core)
					Clockwork.collectionHint(collection)
					complete(Pages.front(user, collection))
				}
			} ~
			path("v" / Segment / IntNumber) { (col, pos) =>
				neo.txs {
					rejectNone(Vault.find.collection(col)) { cn =>
						rejectNone(cn(pos)) { en =>
							Clockwork.archiveHint(en)
							complete(Pages.view(user, en))
						}
					}
				}
			} ~
			path("i" / LongNumber) { id =>
				neo.txs {
					Vault.byID(id) match {
						case Good(asset @ Asset(_)) =>
							Clockwork.archiveHint(asset)
							complete(Pages.view(user, asset))
						case Good(collection @ Collection(_)) =>
							Clockwork.collectionHint(collection)
							complete(Pages.front(user, collection))
						case other => complete(other.toString)
					}
				}
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
			}

	def rejectNone[T](opt: => Option[T])(route: T => Route) = opt.map { route }.getOrElse(reject)
}
