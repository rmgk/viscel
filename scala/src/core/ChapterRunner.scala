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

	def pageRunner: PageRunner //PageDescription => Future[(PageDescription, Seq[ElementNode])]

	def next(page: PageDescription): Try[PageDescription] = {
		page.pipe {
			case FullPage(_, next, _) => next
			case PagePointer(_, next) => next.map { Try(_) }
		}.pipe {
			case None => Try { throw EndRun(s"no next for ${page.loc}") }
			case Some(tried) => tried
		}
	}

	def process(page: PageDescription): Future[PageDescription] =
		pageRunner(page).flatMap { newPage =>
			pageRunner.nodes(newPage).map { nodes =>
				nodes.foreach { chapterNode.append(_) }
				newPage
			}
		}

	def append(page: PageDescription): Future[Unit] = {
		page.validate(p => !forbidden(p.loc), EndRun(s"not part of this chapter ${page.loc}")).toFuture
			.flatMap { process }
			.flatMap { next(_).toFuture }
			.flatMap { append }
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
				throw FailRun(s"description source does not match node soucre (${ed.source}) (${en[String]("source")})")
		}
		def pageMatchesNode(en: ElementNode) = page match {
			case pp @ PagePointer(_, _) => pointerMatches(pp, en)
			case FullPage(_, _, elements) =>
				elements.validate(_.size > 0, FailRun("can not validate page without elements"))
					.map { elements => descriptionMatches(elements.last, en) }
					.map { _ => page }
		}
		elementNodeExists.flatMap { pageMatchesNode }
	}

	def continue(page: PageDescription): Future[Unit] = {
		pageRunner(page)
			.flatMap { validatePage(chapterNode.last, _).toFuture }
			.flatMap { next(_).toFuture }
			.flatMap { append }
	}

	def update(chap: LinkedChapter): Future[Unit] = {
		def eatElements(en: ElementNode, curr: PageDescription): Option[PageDescription] = {
			if (en[String]("origin") == curr.loc.toString) en.next match {
				case None => Option(curr)
				case Some(next) => eatElements(next, curr)
			}
			else next(curr).toOption.flatMap { eatElements(en, _) }
		}
		chapterNode.first match {
			case None => append(chap.first)
			case Some(fen) =>
				eatElements(fen, chap.first) match {
					case None => continue(PagePointer(chapterNode.last.get.origin))
					case Some(pd) => continue(pd)
				}
		}
	}

}

