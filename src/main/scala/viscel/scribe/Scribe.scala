package viscel.scribe

import java.nio.file.{Files, Path}
import java.util.concurrent.{LinkedBlockingQueue, ThreadPoolExecutor, TimeUnit}

import akka.actor.ActorSystem
import akka.io.IO
import org.scalactic.TypeCheckedTripleEquals._
import spray.can.Http
import spray.client.pipelining
import spray.client.pipelining.SendReceive
import spray.http.{HttpResponse, HttpEncodings}
import viscel.scribe.database.{Books, Neo, NeoInstance, label}
import viscel.scribe.narration.Narrator
import viscel.scribe.store.{BlobStore, Config}

import scala.collection.concurrent
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.util.{Try, Failure, Success}

object Scribe {

	def time[T](desc: String = "")(f: => T): T = {
		val start = System.nanoTime
		val res = f
		Console.println(s"$desc took ${ (System.nanoTime - start) / 1000000.0 } ms")
		res
	}

	def apply(basedir: Path, system: ActorSystem, executionContext: ExecutionContext): Scribe = {

		Files.createDirectories(basedir)

		val neo = new NeoInstance(basedir.resolve("db").toString)

		val configNode = neo.tx { implicit ntx =>
			val cfg = Config.get()(ntx)
			if (cfg.version != 2) throw new IllegalStateException(s"config version not supported: ${ cfg.version }")

			cfg
		}

		neo.tx { implicit ntx =>
			if (!neo.db.schema().getConstraints(label.Book).iterator().hasNext)
				neo.db.schema().constraintFor(label.Book).assertPropertyIsUnique("id").create()
		}

		implicit val system = ActorSystem()

		val ioHttp = IO(Http)
		val iopipe = pipelining.sendReceive(ioHttp)(system.dispatcher, 300.seconds)

		val responseHandler: Try[HttpResponse] => Unit = {
			case Success(res) => neo.tx { ntx =>
				configNode.download(
					size = res.entity.data.length,
					success = res.status.isSuccess,
					compressed = res.encoding === HttpEncodings.deflate || res.encoding === HttpEncodings.gzip)(ntx)
			}
			case Failure(_) => neo.tx { ntx => configNode.download(0, success = false)(ntx) }
		}

		val clockworkContext = ExecutionContext.fromExecutor(new ThreadPoolExecutor(
			0, 1, 1L, TimeUnit.SECONDS, new LinkedBlockingQueue[Runnable]))

		val blobs = new BlobStore(basedir.resolve("blobs"))

		new Scribe(basedir, neo, iopipe, executionContext, blobs, new RunnerUtil(blobs, responseHandler))
	}

}

class Scribe(val basedir: Path, val neo: NeoInstance, val sendReceive: SendReceive, val ec: ExecutionContext, val blobs: BlobStore, val util: RunnerUtil) {

	val runners: concurrent.Map[String, Runner] = concurrent.TrieMap[String, Runner]()

	def finish(narrator: Narrator, runner: Runner, success: Boolean): Unit = {
		runners.remove(narrator.id, runner)
	}

	def ensureRunner(id: String, runner: Runner): Unit = {
		runners.putIfAbsent(id, runner) match {
			case Some(x) => Log.info(s"$id race on job creation")
			case None =>
				runner.init().onSuccess { case (n, r, s) => finish(n, r, s) }(ec)
				ec.execute(runner)
		}
	}

	private val dayInMillis = 24L * 60L * 60L * 1000L

	def runForNarrator(narrator: Narrator, iopipe: SendReceive, neo: Neo): Unit = {
		val id = narrator.id
		if (runners.contains(id)) Log.trace(s"$id has running job")
		else {
			Log.info(s"update ${ narrator.id }")
			val runner = neo.tx { implicit ntx =>
				val collection = Books.findAndUpdate(narrator)
				new Runner(narrator, iopipe, collection, neo, ec, util)
			}
			ensureRunner(id, runner)
		}
	}
}
