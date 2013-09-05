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

trait PageRunner {
	def nodes(page: PageDescription): Future[Seq[ElementNode]]
	def apply(page: PageDescription): Future[PageDescription]

}

trait FullPageRunner extends PageRunner with NetworkPrimitives {

	def wrapPage: Document => Future[FullPage]

	def selectNext(foundPage: FullPage, knownPage: PagePointer): FullPage = {
		knownPage.next match {
			case None => foundPage
			case Some(knownNext) =>
				foundPage.next match {
					case None => foundPage.copy(next = Some(Try(knownNext)))
					case Some(Failure(_)) => foundPage
					case Some(Success(foundNext)) if foundNext.loc == knownNext.loc => foundPage
					case Some(Success(foundNext)) => throw FailRun(s"found next ${foundNext.loc} but expected ${knownNext.loc}")
				}
		}
	}

	def wrap(pp: PagePointer): Future[FullPage] = document(pp.loc).flatMap { wrapPage }.map { selectNext(_, pp) }

	def createElementNode(edata: ElementData): ElementNode = Neo.txs {
		ElementNode.create(
			(edata.description.toMap ++
				Seq("blob" -> edata.sha1,
					"mediatype" -> edata.mediatype.toString)).toSeq: _*)
	}

	def store(elements: Seq[ElementData]): Seq[ElementNode] =
		elements.map { edata =>
			Resource.fromFile(hashToFilename(edata.sha1)).write(edata.buffer)
			createElementNode(edata)
		}

	def apply(page: PageDescription): Future[FullPage] =
		page match {
			case fp: FullPage => Future.successful(fp)
			case pp: PagePointer => wrap(pp)
		}

	def nodes(page: PageDescription): Future[Seq[ElementNode]] =
		apply(page).flatMap { fullPage =>
			elementsData(fullPage.elements)
				.map { store }
		}

}

trait PlaceholderPageRunner extends PageRunner with Logging {

	def createElementNodes(page: PageDescription): Seq[ElementNode] = Neo.txs {
		page match {
			case pp: PagePointer =>
				logger.info(s"create element for ${pp.loc}")
				Seq(ElementNode.create("origin" -> pp.loc.toString))
			case fp: FullPage => fp.elements.map { ed =>
				logger.info(s"create element for ${ed}")
				ElementNode.create(ed.toMap.toSeq: _*)
			}
		}
	}

	def nodes(page: PageDescription): Future[Seq[ElementNode]] = Future.successful { createElementNodes(page) }
	def apply(page: PageDescription): Future[PageDescription] = Future.successful(page)
}

