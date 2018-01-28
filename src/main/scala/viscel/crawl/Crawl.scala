package viscel.crawl

import java.time.Instant

import org.scalactic.{Bad, Every, Good, Or}
import viscel.narration.Narrator
import viscel.scribe.{ImageRef, Book, Link, PageData, Vurl, WebContent}
import viscel.selection.Report
import viscel.shared.Log

import scala.async.Async.{async, await}
import scala.concurrent.{ExecutionContext, Future}

class Crawl(
	narrator: Narrator,
	book: Book,
	requestUtil: RequestUtil)
	(implicit ec: ExecutionContext) {




	var articles: List[ImageRef] = _
	var links: List[Link] = _

	var articlesDownloaded = 0
	var rechecksDone = 0
	var recheckStarted = false
	var requestAfterRecheck = 0
	var recheck: List[Link] = _

	def start(): Future[Boolean] = {
		val entry = book.beginning
		if (entry.isEmpty || entry.get.contents != narrator.archive) {
			book.add(PageData(Vurl.entrypoint, Vurl.entrypoint, date = Instant.now(), contents = narrator.archive))
		}
		articles = book.emptyArticles()
		links = book.volatileAndEmptyLinks()
		nextArticle()
	}

	def nextArticle(): Future[Boolean] = {
		articles match {
			case Nil =>
				nextLink()
			case h :: t =>
				async {
					val blob = await(requestUtil.requestBlob(h.ref, Some(h.origin)))
					articles = t
					articlesDownloaded += 1
					book.add(blob)
					await(nextArticle())
				}
		}
	}


	def nextLink(): Future[Boolean] = {
		links match {
			case Nil =>
				rightmostRecheck()
			case link :: t =>
				links = t
				handleLink(link)
		}
	}

	def handleLink(link: Link): Future[Boolean] = {
		requestAndWrap(link) flatMap {
			case Bad(reports) =>
				Log.error(
					s"""↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓
					   |$narrator
					   |  failed on ${link.ref.uriString()} (${link.policy}${if (link.data.nonEmpty) s", ${link.data}" else ""}):
					   |  ${reports.map {_.describe}.mkString("\n  ")}
					   |↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑""".stripMargin)
				Future.successful(false)
			case Good(page) =>
				addContents(page.contents)
				book.add(page)
				nextArticle()
		}
	}

	def requestAndWrap(link: Link): Future[PageData Or Every[Report]] =
		for {
			(doc, res) <- requestUtil.requestDocument(link.ref)} yield for {
			contents <- narrator.wrap(doc, link)
		} yield
			PageData(link.ref, Vurl.fromString(doc.location()),
				contents = contents,
				date = requestUtil.extractLastModified(res).getOrElse(Instant.now()))

	def rightmostRecheck(): Future[Boolean] = {
		if (!recheckStarted && articlesDownloaded > 0) {
			return Future.successful(true)
		}
		if (!recheckStarted) {
			recheckStarted = true
			recheck = book.rightmostScribePages()
		}
		if (rechecksDone == 0 || (rechecksDone == 1 && requestAfterRecheck > 1)) {
			rechecksDone += 1
			recheck match {
				case Nil => Future.successful(true)
				case link :: tail =>
					recheck = tail
					handleLink(link)
			}
		}
		else {
			Future.successful(true)
		}
	}


	def addContents(contents: List[WebContent]): Unit = {
		if (recheckStarted) {
			if (contents.isEmpty && requestAfterRecheck == 0) requestAfterRecheck += 1
			requestAfterRecheck += 1
		}
		contents.reverse.foreach {
			case link@Link(ref, _, _) if !book.hasPage(ref) => links = link :: links
			case art@ImageRef(ref, _, _) if !book.hasBlob(ref) => articles = art :: articles
			case _ =>
		}
	}
}
