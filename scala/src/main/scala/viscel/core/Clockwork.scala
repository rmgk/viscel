package viscel.core

import akka.actor.{ Props, Actor, ActorRef }
import akka.util.Timeout
import com.typesafe.scalalogging.slf4j.StrictLogging
import viscel.wrapper.{Twokinds, Misfile}
import scala.collection._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util._
import spray.client.pipelining._
import viscel._
import viscel.store._
import org.scalactic.TypeCheckedTripleEquals._

object Clockwork {
	case object EnqueueDefault
	case class Run(id: String)
	case class Enqueue(id: String)
	case class Done(core: Core)
	lazy val availableCores: Seq[Core] = Seq(Misfile, Twokinds)
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
		if (col.name !== core.name) col.name = core.name
		col
	}

	def getCore(id: String) = availableCores.find(_.id === id).get

	def keepUpdated: String => Boolean = x => true //ConfigNode().legacyCollections.toSet

	def wantsUpdate(core: Core) = true // getCollection(core).lastUpdate + 8 * 60 * 60 * 1000 < System.currentTimeMillis

	def receive = {
		case EnqueueDefault =>
			val keepUp = keepUpdated
			time("enqueue") { waitingCores.enqueue(availableCores.filter(core => keepUp(core.id) && wantsUpdate(core)): _*) }
			fillActive()
		case Enqueue(id) =>
			waitingCores.enqueue(getCore(id))
			fillActive()
		case Run(id) => update(getCore(id))
		case Done(core) =>
			val col = getCollection(core)
			activeCores -= core
			fillActive()
			if (activeCores.isEmpty) context.system.scheduler.scheduleOnce(1.hour, self, EnqueueDefault)
		case msg => logger.warn(s"received unexpected message: $msg")
	}

	def fillActive() = while (activeCores.size < maxActive && waitingCores.nonEmpty) {
		update(waitingCores.dequeue())
	}

	def update(core: Core): Boolean = {
		if (activeCores(core)) false
		else {
			activeCores += core
			fullArchive(core)
			true
		}
	}

	def fullArchive(core: Core): Unit = {
		val col = getCollection(core)
		val props = Props(classOf[ActorRunner], iopipe, core, col, self)
		val runner = context.actorOf(props, core.name)
		runner ! "start"
	}
}
