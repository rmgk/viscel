package viscel.core

import akka.actor.{ ActorSystem, Props, Actor }
import akka.io.IO
import com.typesafe.scalalogging.slf4j.Logging
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util._
import spray.can.Http
import spray.client.pipelining._
import spray.http.Uri
import viscel._
import viscel.store._
import spray.http.HttpHeaders.`Content-Type`
import spray.http.HttpHeaders.Location
import spray.http.HttpRequest
import spray.http.HttpResponse
import spray.http.ContentType
import scalax.io._
import scala.collection.JavaConversions._
import org.neo4j.graphdb.Direction

trait ChapterRunner extends Logging {

	def chapterNode: ChapterNode
	def forbidden: Uri => Boolean
	def chapter: LinkedChapter

	def pageRunner: PageRunner //PageDescription => Future[(PageDescription, Seq[ElementNode])]

	def next(page: PageDescription): Try[PageDescription] =
		page match {
			case FullPage(_, next, _, _) => next
			case PagePointer(_, next, _) => next match {
				case None => Try { throw EndRun(s"no next for ${page.loc}") }
				case Some(tried) => Try { tried }
			}
		}

	def process(page: PageDescription): Future[PageDescription] =
		pageRunner(page).flatMap { newPage =>
			pageRunner.nodes(newPage).map { nodes =>
				nodes.foreach { chapterNode.append(_) }
				newPage
			}
		}

	def append(page: PageDescription, processed: Set[Uri] = Set()): Future[Unit] = {
		page.validate(p => !forbidden(p.loc) && !processed(p.loc), EndRun(s"already visited/forbidden ${page.loc}")).toFuture
			.flatMap { process }
			.flatMap { next(_).toFuture }
			.flatMap { append(_, processed + page.loc) }
	}

	def validatePage(oen: Option[ElementNode], page: PageDescription): Try[PageDescription] = {
		def elementNodeExists = oen match {
			case None => Try { throw FailRun("can not check empty element") }
			case Some(en) => Try { en }
		}
		def pointerMatches(pp: PagePointer, en: ElementNode) = {
			if (en.origin == pp.loc.toString) Try { pp }
			else Try { throw FailRun(s"element origin does not match page location (${en.origin}) (${pp.loc})") }
		}
		def descriptionMatches(ed: ElementDescription, en: ElementNode) = {
			if (ed.origin.toString != en.origin)
				throw FailRun(s"description origin does not match node origin (${ed.origin}) (${en.origin})")
			if (ed.source.toString != en[String]("source"))
				throw FailRun(s"description source does not match node source (${ed.source}) (${en[String]("source")})")
		}
		def pageMatchesNode(en: ElementNode) = page match {
			case pp @ PagePointer(_, _, _) => pointerMatches(pp, en)
			case FullPage(_, _, elements, _) =>
				elements.validate(_.size > 0, FailRun("can not validate page without elements"))
					.map { elements => descriptionMatches(elements.last, en) }
					.map { _ => page }
		}
		elementNodeExists.flatMap { pageMatchesNode }
	}

	def backtrack(page: PageDescription): Future[Unit] = {
		chapterNode.last match {
			case Some(last) if last.origin == page.loc.toString =>
				chapterNode.dropLast(last.origin)
				backtrack(page)
			case _ => update()
		}
	}

	def continue(page: PageDescription): Future[Unit] = {
		val p = Promise[Unit]()

		pageRunner(page).onComplete {
			case Success(pd) =>
				validatePage(chapterNode.last, pd) match {
					case Success(pd) =>
						next(pd).toFuture
							.flatMap { append(_, Set(page.loc)) }
							.pipe { p.completeWith }
					case Failure(e) =>
						logger.warn(s"failed validation, backtracking (${e.getMessage})")
						p.completeWith(backtrack(page))
				}
			case Failure(f) => f match {
				case e: CoreStatus =>
					logger.warn(s"failure on page for validation, backtracking (${e.getMessage})")
					p.completeWith(backtrack(page))
			}
		}

		p.future
	}

	def update(): Future[Unit] = {
		def eatElements(en: ElementNode, curr: PageDescription, eaten: Boolean = false): Option[PageDescription] = {
			if (en[String]("origin") == curr.loc.toString) en.next match {
				case None => Option(curr)
				case Some(next) => eatElements(next, curr, true)
			}
			else {
				if (!eaten) logger.info(s"warning: skip page without elements ${curr.loc}")
				next(curr).toOption.flatMap { eatElements(en, _, false) }
			}
		}
		chapterNode.first match {
			case None => append(chapter.first)
			case Some(fen) =>
				eatElements(fen, chapter.first) match {
					case None => continue(PagePointer(chapterNode.last.get.origin))
					case Some(pd) => continue(pd)
				}
		}
	}

}

