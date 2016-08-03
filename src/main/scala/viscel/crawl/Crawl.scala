package viscel.crawl

import java.nio.file.Path

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.Materializer
import viscel.narration.Narrator
import viscel.scribe.BlobStore

import scala.concurrent.{ExecutionContext, Future}

class Crawl(requestUtil: RequestUtil) {



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
