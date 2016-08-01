package viscel.scribe

import java.awt.print.Book
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.HttpEncodings
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.Materializer
import org.scalactic.TypeCheckedTripleEquals._
import viscel.scribe.crawl.CrawlerUtil
import viscel.scribe.database.Books
import viscel.scribe.narration.Narrator
import viscel.scribe.store.BlobStore

import scala.collection.concurrent
import scala.concurrent.duration.{FiniteDuration, SECONDS}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._
import viscel.scribe.store.Json._

object Scribe {

	def apply(basedir: Path, system: ActorSystem, materializer: Materializer, executionContext: ExecutionContext): Scribe = {

		Files.createDirectories(basedir)
		val bookdir = basedir.resolve("db3/books")
		Files.createDirectories(bookdir)


		val ioHttp: HttpExt = Http(system)
		val iopipe = (request: HttpRequest) => ioHttp.singleRequest(request)(materializer)

		val responseHandler: Try[HttpResponse] => Unit = {
			case Success(res) => println("should handle stats")
			case Failure(_) => println("should handle stats")
		}

		val blobs = new BlobStore(basedir.resolve("blobs"))

		new Scribe(
			basedir = basedir,
			sendReceive = iopipe,
			ec = executionContext,
			blobs = blobs,
			util = new CrawlerUtil(blobs, responseHandler)(executionContext, materializer),
			books = new Books(bookdir)
		)
	}

}

class Scribe(
	val basedir: Path,
	val sendReceive: HttpRequest => Future[HttpResponse],
	val ec: ExecutionContext,
	val blobs: BlobStore,
	val util: CrawlerUtil,
	val books: Books
	) {


	def runForNarrator(narrator: Narrator): Future[Boolean] = ???

	//	val books = new Books(neo)
//
//	val runners: concurrent.Map[String, Crawler] = concurrent.TrieMap[String, Crawler]()
//
//	def purge(id: String): Boolean = neo.tx { implicit ntx =>
//		val book = books.findExisting(id)
//		book.foreach(_.self.deleteRecursive)
//		book.isDefined
//	}
//
//	def finish(runner: Crawler): Unit = {
//		runners.remove(runner.narrator.id, runner)
//	}
//
//	def ensureRunner(id: String, runner: Crawler): Future[Boolean] = {
//		runners.putIfAbsent(id, runner) match {
//			case Some(x) =>
//				Log.info(s"$id race on job creation")
//				Future.successful(false)
//			case None =>
//				val result = runner.init()
//				result.onComplete { _ => finish(runner) }(ec)
//				ec.execute(runner)
//				result
//		}
//	}
//
//	private val dayInMillis = 24L * 60L * 60L * 1000L
//
//	def runForNarrator(narrator: Narrator): Future[Boolean] = {
//		val id = narrator.id
//		if (runners.contains(id)) {
//			Log.info(s"$id has running job")
//			Future.successful(false)
//		}
//		else {
//			Log.info(s"update ${narrator.id}")
//			val runner = neo.tx { implicit ntx =>
//				val collection = books.findAndUpdate(narrator)
//				new Crawler(narrator, sendReceive, collection, neo, ec, util)
//			}
//			ensureRunner(id, runner)
//		}
//	}

}
