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

trait ChapterRunner {

	def chapterNode: ChapterNode
	def forbidden: Uri => Boolean
	// def wrapPage: Document => FullPage

	def pageRunner: PageDescription => Future[(PageDescription, Seq[ElementNode])]
	// val pageRunner = new FullPageRunner {
	// 	override def iopipe = ChapterRunner.this.iopipe
	// 	def wrapPage = ChapterRunner.this.wrapPage
	// }

	def next(page: PageDescription): Future[PageDescription] = {
		page.pipe {
			case FullPage(_, next, _) => next
			case PagePointer(_, next) => next.map { Try(_) }
		}.pipe {
			case None => Future.failed(EndRun(s"no next for ${page.loc}"))
			case Some(tried) => tried.toFuture
		}
	}

	def process(page: PageDescription): Future[PageDescription] =
		pageRunner(page)
			.map {
				case (newPage, nodes) =>
					nodes.foreach { chapterNode.append(_) }
					newPage
			}

	def continue(page: PageDescription): Future[Unit] = {
		page.validate(p => !forbidden(p.loc), EndRun(s"already seen ${page.loc}")).toFuture
			.flatMap { process }
			.flatMap { next }
			.flatMap { continue }
	}

}

