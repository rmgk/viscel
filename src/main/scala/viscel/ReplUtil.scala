package viscel

import akka.actor.ActorSystem
import org.jsoup.nodes.Document
import spray.client.pipelining.SendReceive
import viscel.crawler.RunnerUtil
import viscel.narration.{Metarrator, Narrator, SelectUtil}
import viscel.shared.ViscelUrl

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

class ReplUtil(val system: ActorSystem, val iopipe: SendReceive) {
	def fetch(url: String): Document = fetchV(SelectUtil.stringToVurl(url))

	def fetchV(vurl: ViscelUrl): Document = {
		val request = RunnerUtil.request(vurl)
		val res = RunnerUtil.getResponse(request, iopipe).map { RunnerUtil.parseDocument(vurl) }
		res.onFailure { case t: Throwable =>
			Log.error(s"error fetching $vurl")
			t.printStackTrace()
		}
		Await.result(res, Duration.Inf)
	}

	def updateMetarrator[T <: Narrator](metarrator: Metarrator[T]) = {
		val doc = fetchV(metarrator.archive)
		val nars = metarrator.wrap(doc)
		metarrator.save(nars.get)
	}

	def shutdown() = {
		system.shutdown()
		Viscel.neo.shutdown()
	}
}

object ReplUtil {
	def apply() = {
		val (system, ioHttp, iopipe) = Viscel.run()
		new ReplUtil(system, iopipe)
	}
}
