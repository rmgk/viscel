package viscel.crawler

import akka.actor.{Actor, ActorRef, Props}
import akka.util.Timeout
import com.typesafe.scalalogging.slf4j.StrictLogging
import spray.client.pipelining._
import viscel.crawler.Messages._
import viscel.cores.Core

import scala.Predef.any2ArrowAssoc
import scala.collection.immutable._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class Clockwork(ioHttp: ActorRef) extends Actor with StrictLogging {

	var activeCores = Map[Core, ActorRef]()

	val iopipe = {
		implicit val timeout: Timeout = 300.seconds
		//import system.dispatcher
		sendReceive(ioHttp)
	}

	def receive = {
		case Run(core) =>
			logger.info(s"starting runner for $core")
			runnerFor(core)
		case Done(core, timeout, failure) => activeCores.get(core).fold(ifEmpty = logger.warn(s"got Done from unknown core $core")) { actor =>
			logger.info(s"core $core is done. stopping")
			context.stop(actor)
			activeCores -= core
		}
		case hint@ArchiveHint(archiveNode) =>
			val col = archiveNode.collection
			Core.get(col.id) match {
				case None => logger.warn(s"unkonwn core ${ col.id }")
				case Some(core) => runnerFor(core) ! hint
			}

		case hint@CollectionHint(collectionNode) =>
			Core.get(collectionNode.id) match {
				case None => logger.warn(s"unkonwn core ${ collectionNode.id }")
				case Some(core) => runnerFor(core) ! hint
			}


		case msg => logger.warn(s"received unexpected message: $msg")
	}

	def runnerFor(core: Core): ActorRef = {
		activeCores.getOrElse(core, {
			val actor = makeRunner(core)
			activeCores += core -> actor
			actor
		})
	}

	def makeRunner(core: Core): ActorRef = {
		val col = Core.getCollection(core)
		val props = Props(Predef.classOf[ActorRunner], iopipe, core, col, self)
		val runner = context.actorOf(props, core.id)
		runner
	}
}
