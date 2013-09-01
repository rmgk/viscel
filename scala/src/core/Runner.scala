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

trait Runner extends NetworkPrimitives {

	def wrapPage: Document => WrappedPage
	def collection: NodeContainer[ElementNode]

	def wrap(loc: Uri): Future[WrappedPage] = loc.pipe { document }.map { wrapPage }

	def createElementNode(edata: ElementData): ElementNode = Neo.txs {
		ElementNode.create(
			(edata.element.toMap ++
				Seq("blob" -> edata.sha1,
					"mediatype" -> edata.mediatype.toString)).toSeq: _*)
			.pipe(collection.append)
			.tap { en => logger.info(s"""create element node ${en.nid} pos ${en.position} ${en("source")}""") }
	}

	def store(elements: Seq[ElementData]): Unit =
		elements.foreach { edata =>
			Resource.fromFile(hashToFilename(edata.sha1)).write(edata.buffer)
			createElementNode(edata)
		}

	def find(loc: Uri) = Neo.txs {
		collection.children.filter { node => Uri(node[String]("origin")) == loc }.toIndexedSeq
	}

	def validateSeq[A](xs: Seq[Try[A]]): Try[Seq[A]] =
		xs.find(_.isFailure) match {
			case Some(f) => f.asInstanceOf[Try[Seq[A]]]
			case None => Try { xs.map(_.get) }
		}

	def get(wrapped: WrappedPage): Future[WrappedPage] =
		validateSeq(wrapped.elements).toFuture
			.flatMap { elementsData }
			.map { elements =>
				store(elements)
				wrapped
			}

	def next(wrapped: WrappedPage): Future[Uri] = wrapped.next.toFuture

	def matches(wrapped: WrappedPage): Future[WrappedPage] = {
		wrapped.elements.last.get.similar(collection.last.get)
		Future.successful(wrapped)
	}

	def continue(loc: Uri): Future[Unit] = {
		loc.validate(find(_).isEmpty, endRun(s"already seen $loc")).toFuture
			.flatMap { wrap }
			.flatMap { get }
			.flatMap { next }
			.flatMap { continue }
	}

	def continueAfter(loc: Uri): Future[Unit] =
		wrap(loc)
			.flatMap { matches }
			.flatMap { next }
			.flatMap { continue }

	def start(first: Uri) = collection.last match {
		case None => continue(first)
		case Some(last) => continueAfter(last[String]("origin"))
	}

}

