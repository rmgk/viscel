package viscel.core

import akka.actor.{ ActorSystem, Props, Actor }
import akka.io.IO
import akka.pattern.AskTimeoutException
import com.typesafe.scalalogging.slf4j.Logging
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

class Clockwork(val iopipe: SendReceive) extends Logging {

	// val cores = DCore.list.cores ++ Seq(CarciphonaWrapper, FlipsideWrapper, FreakAngelsWrapper)

	// // def nonChaptered() = cores.foreach { core =>
	// // 	logger.info(s"start core ${core.id}")

	// // 	new Runner {
	// // 		def iopipe = Clockwork.this.iopipe
	// // 		def wrapPage = core.wrapPage _
	// // 		def collection = CollectionNode(core.id).getOrElse(CollectionNode.create(core.id, Some(core.name)))
	// // 	}.start(core.first).onComplete {
	// // 		case Success(_) => logger.info("test complete without errors")
	// // 		case Failure(e) => e match {
	// // 			case e: EndRun => logger.info(s"${core.id} complete ${e}")
	// // 			case e => logger.info(s"${core.id} failed ${e}"); e.printStackTrace
	// // 		}
	// // 	}
	// // }

	// def chaptered() = DrMcNinjaWrapper.pipe { core =>
	// 	new ChapteredRunner {
	// 		def iopipe = Clockwork.this.iopipe
	// 		def wrapChapter = core.wrapChapter _
	// 		def wrapPage = core.wrapPage _
	// 		def collection = CollectionNode(core.id).getOrElse(CollectionNode.create(core.id, Some(core.name)))
	// 	}.start(core.first).onComplete {
	// 		case Success(_) => logger.info("test complete without errors")
	// 		case Failure(e) => e match {
	// 			case e: EndRun => logger.info(s"${core.id} complete ${e}")
	// 			case e => logger.info(s"${core.id} failed ${e}"); e.printStackTrace
	// 		}
	// 	}
	// }

	def fullArchive(ocore: Core) = {
		new ArchiveRunner {
			def collection = CollectionNode(ocore.id).getOrElse(CollectionNode.create(ocore.id, ocore.name))
			def core = ocore
			def iopipe = Clockwork.this.iopipe
		}.update().onComplete {
			case Success(_) => logger.info("test complete without errors")
			case Failure(e) => e match {
				case e: NormalStatus => logger.info(s"${ocore.id} complete ${e}")
				case e: FailedStatus =>
					logger.info(s"${ocore.id} failed ${e}"); e.printStackTrace
				case e: AskTimeoutException => logger.info(s"${ocore.id} timed out (system shutdown?)")
				case e: Throwable => logger.info(s"${ocore.id} unexpected error ${e}"); e.printStackTrace
			}
		}
	}

	def test() = for (core <- Seq(PhoenixRequiem, MarryMe, InverlochArchive, TwokindsArchive, Avengelyne, FreakAngels)) fullArchive(core)
}
