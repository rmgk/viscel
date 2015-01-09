package viscel

import akka.actor.ActorSystem
import org.jsoup.nodes.Document
import spray.client.pipelining.SendReceive
import viscel.crawler.{Clockwork, RunnerUtil}
import viscel.shared.AbsUri
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

class ReplUtil(val system: ActorSystem, val iopipe: SendReceive) {
	def fetch(absUri: AbsUri): Document = {
		val request = RunnerUtil.request(absUri)
		val res = RunnerUtil.getResponse(request, iopipe).map { RunnerUtil.parseDocument(absUri) }
		res.onFailure { case t: Throwable =>
			Log.error(s"error fetching $absUri")
			t.printStackTrace()
		}
		Await.result(res, Duration.Inf)
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
