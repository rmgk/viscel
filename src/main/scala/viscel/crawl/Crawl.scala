package viscel.crawl

import java.time.Instant

import org.scalactic.{Bad, Good}
import viscel.narration.Narrator
import viscel.scribe.{ArticleRef, Book, Link, Scribe, ScribePage, Vurl, WebContent}
import viscel.shared.Log

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

class Crawl(narrator: Narrator, scribe: Scribe, requestUtil: RequestUtil)(implicit ec: ExecutionContext) extends Runnable {


	val book: Book = scribe.findOrCreate(narrator)
	val promise = Promise[Boolean]()

	var articles: List[ArticleRef] = _
	var links: List[Link] = _

	def start(): Future[Boolean] = {
		val entry = book.pageMap.get(Vurl.entrypoint)
		if (entry.isEmpty || entry.get.contents != narrator.archive) {
			book.add(ScribePage(Vurl.entrypoint, Vurl.entrypoint, date = Instant.now(), contents = narrator.archive))
		}
		articles = book.emptyArticles()
		links = book.emptyLinks()
		ec.execute(this)
		promise.future
	}

	override def run(): Unit = synchronized {
		nextArticle()
	}

	def nextArticle(): Unit = {
		articles match {
			case Nil =>
				nextLink()
			case h :: t => requestUtil.requestBlob(h.ref, Some(h.origin)).onComplete {
				case Failure(e) => promise.failure(e)
				case Success(blob) =>
					articles = t
					book.add(blob)
					ec.execute(this)
			}
		}
	}

	def nextLink(): Unit = {
		links match {
			case Nil =>
				promise.success(true)
			case link :: t =>
				requestUtil.request(link.ref).flatMap { res =>
					requestUtil.extractDocument(link.ref)(res).map { doc =>
						narrator.wrap(doc, link) match {
							case Bad(reports) =>
								Log.error(s"$narrator failed on $link: ${reports.map {_.describe}}")
								promise.success(false)
							case Good(contents) =>
								val page = ScribePage(link.ref, Vurl.fromString(doc.location()),
									contents = contents,
									date = requestUtil.extractLastModified(res).getOrElse(Instant.now()))
								links = t
								addContents(page.contents)
								book.add(page)
								ec.execute(this)
						}
					}
				}.onFailure(PartialFunction(promise.failure))
		}
	}

	def addContents(contents: List[WebContent]): Unit = {
		contents.foreach {
			case link @ Link(ref, _, _) if !book.pageMap.contains(ref) => links = links ::: link :: Nil
			case art @  ArticleRef(ref, _, _) if !book.blobMap.contains(ref) => articles = articles ::: art :: Nil
			case _ =>
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
