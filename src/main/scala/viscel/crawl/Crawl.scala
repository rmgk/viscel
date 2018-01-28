package viscel.crawl

import java.time.Instant

import org.scalactic.{Bad, Every, Good, Or}
import viscel.narration.Narrator
import viscel.scribe.{ArticleRef, Book, Link, Scribe, ScribePage, Vurl, WebContent}
import viscel.selection.Report
import viscel.shared.Log

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class Crawl(
	narrator: Narrator,
	scribe: Scribe,
	requestUtil: RequestUtil,
	ec: ExecutionContext)
	(promise: Try[Boolean] => Unit)
	extends Runnable {

	implicit def executionContext: ExecutionContext = ec

	val book: Book = scribe.findOrCreate(narrator)


	var articles: List[ArticleRef] = _
	var links: List[Link] = _

	var articlesDownloaded = 0
	var rechecksDone = 0
	var recheckStarted = false
	var requestAfterRecheck = 0
	var recheck: List[Link] = _

	def start(): Unit = {
		val entry = book.beginning
		if (entry.isEmpty || entry.get.contents != narrator.archive) {
			book.add(ScribePage(Vurl.entrypoint, Vurl.entrypoint, date = Instant.now(), contents = narrator.archive))
		}
		articles = book.emptyArticles()
		links = book.volatileAndEmptyLinks()
		ec.execute(this)
	}

	override def run(): Unit = nextArticle()

	def nextArticle(): Unit = {
		articles match {
			case Nil =>
				nextLink()
			case h :: t =>
				requestUtil.requestBlob(h.ref, Some(h.origin)).onComplete {
					case Failure(e) => promise(Failure(e))
					case Success(blob) =>
						articles = t
						articlesDownloaded += 1
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
		requestAndWrap(link).onComplete {
			case Success(Bad(reports)) =>
				Log.error(
					s"""↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓
					   |$narrator
					   |  failed on ${link.ref.uriString()} (${link.policy}${if (link.data.nonEmpty) s", ${link.data}" else ""}):
					   |  ${reports.map {_.describe}.mkString("\n  ")}
					   |↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑""".stripMargin)
				promise(Success(false))
			case Success(Good(page)) =>
				addContents(page.contents)
				book.add(page)
				ec.execute(this)
			case Failure(e) => promise(Failure(e))
		}
	}

	def requestAndWrap(link: Link): Future[Or[ScribePage, Every[Report]]] = {
		requestUtil.requestDocument(link.ref).map { case (doc, res) =>
			narrator.wrap(doc, link).map { contents =>
				ScribePage(link.ref, Vurl.fromString(doc.location()),
					contents = contents,
					date = requestUtil.extractLastModified(res).getOrElse(Instant.now()))
			}
		}
	}

	def rightmostRecheck(): Unit = {
		if (!recheckStarted && articlesDownloaded > 0) {
			promise(Success(true))
			return
		}
		if (!recheckStarted) {
			recheckStarted = true
			recheck = book.rightmostScribePages()
		}
		if (rechecksDone == 0 || (rechecksDone == 1 && requestAfterRecheck > 1)) {
			rechecksDone += 1
			recheck match {
				case Nil => promise(Success(true))
				case link :: tail =>
					recheck = tail
					handleLink(link)
			}
		}
		else {
			promise(Success(true))
		}
	}


	def addContents(contents: List[WebContent]): Unit = {
		if (recheckStarted) {
			if (contents.isEmpty && requestAfterRecheck == 0) requestAfterRecheck += 1
			requestAfterRecheck += 1
		}
		contents.reverse.foreach {
			case link@Link(ref, _, _) if !book.hasPage(ref) => links = link :: links
			case art@ArticleRef(ref, _, _) if !book.hasBlob(ref) => articles = art :: articles
			case _ =>
		}
	}
}
