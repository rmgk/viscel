package viscel.crawler

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.scalactic.ErrorMessage
import spray.client.pipelining.SendReceive
import viscel.database.{ArchiveManipulation, Neo, Ntx}
import viscel.narration.Narrator
import viscel.store.Collection

import scala.concurrent.{ExecutionContext, Future}


class Runner(core: Narrator, iopipe: SendReceive, collection: Collection, neo: Neo, ec: ExecutionContext) extends StrictLogging {

	override def toString: String = s"Job(${ core.toString })"

	def start(): Future[List[ErrorMessage]] = {
		neo.tx { ArchiveManipulation.applyNarration(collection.self, core.archive)(_) }
		run(strategy)
	}

	private def run[R](r: Ntx => Request[R]): Future[R] = {
		val Request(request, handler) = neo.tx { r }
		IOUtil.getResponse(request, iopipe).flatMap { res =>
			neo.tx { handler(res) }
		}(ec)
	}


	def strategy[R](ntx: Ntx): Request[R] = Predef.???

}
