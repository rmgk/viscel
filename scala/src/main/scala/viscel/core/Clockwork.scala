package viscel.core

import akka.actor.{Actor, ActorRef, Props}
import akka.util.Timeout
import com.typesafe.scalalogging.slf4j.StrictLogging
import org.scalactic.TypeCheckedTripleEquals._
import spray.client.pipelining._
import viscel._
import viscel.store._
import viscel.wrapper.{OldBoy, Flipside, Everafter, CitrusSaburoUta, Misfile, Twokinds}

import scala.collection._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object Clockwork {
	case class Run(core: Core)
	case class Done(core: Core)
	lazy val availableCores: Seq[Core] = Seq(OldBoy, Flipside, Everafter, CitrusSaburoUta, Misfile, Twokinds)
	def getCore(id: String) = availableCores.find(_.id === id)
}

class Clockwork(ioHttp: ActorRef) extends Actor with StrictLogging {

	import viscel.core.Clockwork._

	var activeCores = Map[Core, ActorRef]()

	val iopipe = {
		implicit val timeout: Timeout = 30.seconds
		//import system.dispatcher
		sendReceive(ioHttp)
	}

	def getCollection(core: Core) = Neo.txs {
		val col = CollectionNode(core.id).getOrElse(CollectionNode.create(core.id, core.name))
		if (col.name !== core.name) col.name = core.name
		col
	}

	def receive = {
		case Run(core) =>
			logger.info(s"starting runner for $core")
			update(core)
		case Done(core) => activeCores.get(core).fold(ifEmpty = logger.warn(s"got Done from unknown core $core")){ actor =>
			logger.info(s"core $core is done. stopping")
			context.stop(actor)
			activeCores -= core
		}
		case msg => logger.warn(s"received unexpected message: $msg")
	}

	def update(core: Core): ActorRef = {
		activeCores.getOrElse(core, {
			val actor = run(core)
			activeCores += core -> actor
			actor
		})
	}

	def run(core: Core): ActorRef = {
		val col = getCollection(core)
		val props = Props(classOf[ActorRunner], iopipe, core, col, self)
		val runner = context.actorOf(props, core.id)
		runner ! "start"
		runner
	}
}
