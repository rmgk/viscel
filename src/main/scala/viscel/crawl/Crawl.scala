package viscel.crawl

import java.time.Instant

import org.scalactic.{Bad, Every, Good, Or}
import viscel.narration.Narrator
import viscel.scribe.{ArticleRef, Book, Link, Scribe, ScribePage, Vurl, WebContent}
import viscel.selection.Report
import viscel.shared.Log

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

class Crawl(narrator: Narrator, scribe: Scribe, requestUtil: RequestUtil)(implicit ec: ExecutionContext) extends Runnable {


	val book: Book = scribe.findOrCreate(narrator)
	val promise = Promise[Boolean]()

	var articles: List[ArticleRef] = _
	var links: List[Link] = _

	var rechecksDone = 0
	var recheckStarted = false
	var requestAfterRecheck = 0
	var recheck: List[Link] = _

	def start(): Future[Boolean] = {
		val entry = book.pageMap.get(Vurl.entrypoint)
		if (entry.isEmpty || entry.get.contents != narrator.archive) {
			book.add(ScribePage(Vurl.entrypoint, Vurl.entrypoint, date = Instant.now(), contents = narrator.archive))
		}
		articles = book.emptyArticles()
		links = book.volatileAndEmptyLinks()
		ec.execute(this)
		promise.future
	}

	override def run(): Unit = {
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
				rightmostRecheck()
			case link :: t =>
				links = t
				handleLink(link)
		}
	}

	def handleLink(link: Link): Unit = {
		requestAndWrap(link).map {
			case Bad(reports) =>
				Log.error(s"$narrator failed on $link: ${reports.map {_.describe}}")
				promise.success(false)
			case Good(page) =>
				addContents(page.contents)
				book.add(page)
				ec.execute(this)
		}.onFailure(PartialFunction(promise.failure))
	}

	def requestAndWrap(link: Link): Future[Or[ScribePage, Every[Report]]] = {
		requestUtil.request(link.ref).flatMap { res =>
			requestUtil.extractDocument(link.ref)(res).map { doc =>
				narrator.wrap(doc, link).map { contents =>
					ScribePage(link.ref, Vurl.fromString(doc.location()),
						contents = contents,
						date = requestUtil.extractLastModified(res).getOrElse(Instant.now()))
				}
			}
		}
	}

	def rightmostRecheck() = {
		if (!recheckStarted) {
			recheckStarted = true
			recheck = book.rightmostScribePages()
		}
		if (rechecksDone == 0 || (rechecksDone == 1 && requestAfterRecheck > 1)) {
			rechecksDone += 1
			recheck match {
				case Nil => promise.success(true)
				case link :: tail =>
					recheck = tail
					handleLink(link)
			}
		}
		else {
			promise.success(true)
		}
	}


	def addContents(contents: List[WebContent]): Unit = {
		if (recheckStarted) requestAfterRecheck += 1
		contents.foreach {
			case link@Link(ref, _, _) if !book.pageMap.contains(ref) => links = links ::: link :: Nil
			case art@ArticleRef(ref, _, _) if !book.blobMap.contains(ref) => articles = articles ::: art :: Nil
			case _ =>
		}
	}
}
