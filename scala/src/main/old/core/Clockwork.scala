package viscel.core

import akka.actor.{ ActorSystem, Props, Actor, ActorRef }
import akka.io.IO
import akka.pattern.AskTimeoutException
import akka.util.Timeout
import com.typesafe.scalalogging.slf4j.StrictLogging
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.neo4j.graphdb.Direction
import scala.collection.JavaConversions._
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util._
import scalax.io._
import spray.can.Http
import spray.client.pipelining._
import spray.http.ContentType
import spray.http.HttpHeaders.`Content-Type`
import spray.http.HttpHeaders.Location
import spray.http.HttpRequest
import spray.http.HttpResponse
import spray.http.Uri
import viscel._
import viscel.store._
import scala.collection._

object Clockwork {
	case object EnqueueDefault
	case class Run(id: String)
	case class Enqueue(id: String)
	case class Done(core: Core, status: Try[Unit])
	lazy val availableCores: Seq[Core] = LegacyCores.list ++ Seq(PhoenixRequiem, MarryMe, InverlochArchive, TwokindsArchive, Avengelyne, FreakAngels, AmazingAgentLuna, SpyingWithLana, Misfile)
}

class Clockwork(ioHttp: ActorRef) extends Actor with StrictLogging {
	import Clockwork._

	var activeCores = Set[Core]()
	val maxActive = 2
	val waitingCores = mutable.Queue[Core]()

	val iopipe = {
		implicit val timeout: Timeout = 30.seconds
		//import system.dispatcher
		sendReceive(ioHttp)
	}

	def getCollection(core: Core) = {
		val col = CollectionNode(core.id).getOrElse(CollectionNode.create(core.id, core.name))
		if (col.name != core.name) col.name = core.name
		col
	}

	def getCore(id: String) = availableCores.find(_.id === id).get

	def keepUpdated: Set[String] = ConfigNode().legacyCollections.toSet

	def wantsUpdate(core: Core) = (getCollection(core).lastUpdate + 8 * 60 * 60 * 1000 < System.currentTimeMillis)

	def receive = {
		case EnqueueDefault =>
			val keepUp = keepUpdated
			time("enqueue") { waitingCores.enqueue(availableCores.filter(core => keepUp(core.id) && wantsUpdate(core)): _*) }
			fillActive()
		case Enqueue(id) =>
			waitingCores.enqueue(getCore(id))
			fillActive()
		case Run(id) => update(getCore(id))
		case Done(core, status) =>
			val col = getCollection(core)
			status match {
				case Success(_) =>
					col.lastUpdate = System.currentTimeMillis
					logger.info(s"${core.id} complete without errors")
				case Failure(e) => e match {
					case e: NormalStatus =>
						col.lastUpdate = System.currentTimeMillis
						logger.info(s"${core.id} complete ${e}")
					case e: FailedStatus if e.getMessage.startsWith("invalid response 302 Found") =>
						col.lastUpdate = System.currentTimeMillis
						logger.info(s"${core.id} 302 location workaround ${e}")
					case e: FailedStatus =>
						logger.info(s"${core.id} failed ${e}"); e.printStackTrace
					case e: AskTimeoutException => logger.info(s"${core.id} timed out (system shutdown?)")
					case e: Throwable => logger.info(s"${core.id} unexpected error ${e}"); e.printStackTrace
				}
			}
			activeCores -= core
			fillActive()
			if (activeCores.isEmpty) context.system.scheduler.scheduleOnce(1.hour, self, EnqueueDefault)
		case msg => logger.warn(s"received unexpected message: $msg")
	}

	def fillActive() = while (activeCores.size < maxActive && !waitingCores.isEmpty) {
		update(waitingCores.dequeue)
	}

	def update(core: Core): Boolean = {
		if (activeCores(core)) false
		else {
			activeCores += core
			fullArchive(core)
			true
		}
	}

	def fullArchive(ocore: Core): Future[Unit] = {
		val col = getCollection(ocore)
		new ArchiveRunner {
			def collection = col
			def core = ocore
			def iopipe = Clockwork.this.iopipe
		}.update().andThen {
			case status =>
				self ! Done(ocore, status)
		}
	}
}
