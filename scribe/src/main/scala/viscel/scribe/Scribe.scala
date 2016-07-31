package viscel.scribe

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.HttpEncodings
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.Materializer
import org.scalactic.TypeCheckedTripleEquals._
import viscel.scribe.appendstore.{AppendLogEntry, FromNeoBook}
import viscel.scribe.crawl.{Crawler, CrawlerUtil}
import viscel.scribe.database.{Books, NeoInstance, label}
import viscel.scribe.narration.Narrator
import viscel.scribe.store.Config.ConfigNode
import viscel.scribe.store.{BlobStore, Config}
import viscel.scribe.database.Implicits.NodeOps

import scala.collection.concurrent
import scala.concurrent.duration.{FiniteDuration, SECONDS}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._
import viscel.scribe.store.Json._

object Scribe {

	def time[T](desc: String = "")(f: => T): T = {
		val start = System.nanoTime
		val res = f
		Console.println(s"$desc took ${(System.nanoTime - start) / 1000000.0} ms")
		res
	}

	def apply(basedir: Path, system: ActorSystem, materializer: Materializer, executionContext: ExecutionContext): Scribe = {

		Files.createDirectories(basedir)

		val neo = new NeoInstance(basedir.resolve("db2").toString)

		val configNode = neo.tx { implicit ntx =>
			val cfg = Config.get()(ntx)
			if (cfg.version != 2) throw new IllegalStateException(s"config version not supported: ${cfg.version}")

			cfg
		}

		neo.tx { implicit ntx =>
			if (!neo.db.schema().getConstraints(label.Book).iterator().hasNext)
				neo.db.schema().constraintFor(label.Book).assertPropertyIsUnique("id").create()
		}

		val ioHttp: HttpExt = Http(system)
		val iopipe = (request: HttpRequest) => ioHttp.singleRequest(request)(materializer)

		val responseHandler: Try[HttpResponse] => Unit = {
			case Success(res) => neo.tx { ntx =>
				configNode.download(
					size = Await.result(res.entity.toStrict(FiniteDuration(5, SECONDS))(materializer), FiniteDuration(5, SECONDS)).contentLength,
					success = res.status.isSuccess,
					compressed = res.encoding === HttpEncodings.deflate || res.encoding === HttpEncodings.gzip)(ntx)
			}
			case Failure(_) => neo.tx { ntx => configNode.download(0, success = false)(ntx) }
		}

		val blobs = new BlobStore(basedir.resolve("blobs"))

		new Scribe(
			basedir = basedir,
			neo = neo,
			sendReceive = iopipe,
			ec = executionContext,
			blobs = blobs,
			util = new CrawlerUtil(blobs, responseHandler)(executionContext, materializer),
			cfg = configNode)
	}

}

class Scribe(
	val basedir: Path,
	val neo: NeoInstance,
	val sendReceive: HttpRequest => Future[HttpResponse],
	val ec: ExecutionContext,
	val blobs: BlobStore,
	val util: CrawlerUtil,
	val cfg: ConfigNode
	) {

	val books = new Books(neo)

	val runners: concurrent.Map[String, Crawler] = concurrent.TrieMap[String, Crawler]()

	def purge(id: String): Boolean = neo.tx { implicit ntx =>
		val book = books.findExisting(id)
		book.foreach(_.self.deleteRecursive)
		book.isDefined
	}

	def finish(runner: Crawler): Unit = {
		runners.remove(runner.narrator.id, runner)
	}

	def ensureRunner(id: String, runner: Crawler): Future[Boolean] = {
		runners.putIfAbsent(id, runner) match {
			case Some(x) =>
				Log.info(s"$id race on job creation")
				Future.successful(false)
			case None =>
				val result = runner.init()
				result.onComplete { _ => finish(runner) }(ec)
				ec.execute(runner)
				result
		}
	}

	private val dayInMillis = 24L * 60L * 60L * 1000L

	def runForNarrator(narrator: Narrator): Future[Boolean] = {
		val id = narrator.id
		if (runners.contains(id)) {
			Log.info(s"$id has running job")
			Future.successful(false)
		}
		else {
			Log.info(s"update ${narrator.id}")
			val runner = neo.tx { implicit ntx =>
				val collection = books.findAndUpdate(narrator)
				new Crawler(narrator, sendReceive, collection, neo, ec, util)
			}
			ensureRunner(id, runner)
		}
	}

	def convertToAppendLog(): Unit = {
		val dir = basedir.resolve("db3")
		Files.createDirectories(dir)
		books.all().foreach { book =>
			val id = neo.tx { implicit ntx => book.id }
			Log.info(s"make append log for $id")
			val entries = neo.tx { ntx => FromNeoBook.bookToEntries(book)(ntx) }

			val encoded = entries.map(upickle.default.write[AppendLogEntry](_))
			Files.write(dir.resolve(s"$id.json"), encoded.asJava, StandardCharsets.UTF_8, StandardOpenOption.CREATE)
		}
	}
}
