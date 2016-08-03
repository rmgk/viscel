package viscel.crawl

import viscel.narration.Narrator
import viscel.scribe.{Book, Scribe}
import viscel.shared.Log

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

class Crawl(narrator: Narrator, scribe: Scribe, requestUtil: RequestUtil)(implicit ec: ExecutionContext) extends Runnable {


	val book: Book = scribe.findOrCreate(narrator)
	val promise = Promise[Boolean]()

	var articles = book.emptyArticles()

	def start(): Future[Boolean] = {
		ec.execute(this)
		promise.future
	}

	override def run(): Unit = synchronized {
		Log.info(s"running $narrator")
		articles match {
			case Nil =>
				Log.info(s"done $narrator")
				promise.success(false)
			case h :: t => requestUtil.requestBlob(h.ref, Some(h.origin)).onComplete {
				case Failure(e) => promise.failure(e)
				case Success(blob) =>
					articles = t
					book.add(blob)
					ec.execute(this)
			}
		}
	}


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
