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
import spray.http.HttpRequest
import spray.http.HttpResponse
import spray.http.ContentType
import scalax.io._
import scala.collection.JavaConversions._
import org.neo4j.graphdb.Direction

case class ElementData(mediatype: ContentType, sha1: String, buffer: Array[Byte], response: HttpResponse, element: Element)

class Clockwork(val iopipe: SendReceive) extends Logging {

	val cores = Seq(CarciphonaWrapper, FlipsideWrapper, DrMcNinjaWrapper, FreakAngelsWrapper)

	def response(uri: Uri, referer: Option[Uri] = None): Future[HttpResponse] = {
		logger.info(s"get $uri ($referer)")
		val addReferer = referer match {
			case Some(ref) => addHeader("referer", ref.toString)
			case None => (x: HttpRequest) => x
		}
		Get(uri).pipe { addReferer }.pipe { iopipe }
	}

	def document(uri: Uri): Future[Document] = response(uri).map { res => Jsoup.parse(res.entity.asString, uri.toString) }

	def elementData(eseed: Element): Future[ElementData] = {
		response(eseed.source, Some(eseed.origin)).map { res =>
			ElementData(
				mediatype = res.header[`Content-Type`].get.contentType,
				buffer = res.entity.buffer,
				sha1 = sha1hex(res.entity.buffer),
				response = res,
				element = eseed)
		}
	}

	def elementData(wrapped: Wrapped): Future[Seq[ElementData]] = {
		require(wrapped.elements.forall(_.isSuccess), "could not get element: " + (wrapped.elements.filter(_.isFailure).map { _.failed.get.getMessage }))
		wrapped.elements.map(_.get).map { elementData }.pipe { Future.sequence(_) }
	}

	def test() = cores.foreach { core =>
		new Runner(core).start().onComplete {
			case Success(_) => logger.info("test complete without errorrs")
			case Failure(e) => logger.info(s"${core.id} complete ${e.getMessage}")
		}
	}

	def hashToFilename(h: String): String = (new StringBuilder(h)).insert(2, '/').insert(0, "../cache/").toString

	class Runner(core: Core) {
		val collection = CollectionNode(core.id).getOrElse(CollectionNode.create(core.id, Some(core.name)))

		def wrap(loc: Uri): Future[Wrapped] = loc.pipe { document }.map { core.wrapper }

		def createElementNode(edata: ElementData): ElementNode = Neo.txs {
			ElementNode.create(
				(edata.element.toMap ++
					Seq("blob" -> edata.sha1,
						"mediatype" -> edata.mediatype.toString)).toSeq: _*)
				.pipe(collection.append(_, None))
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

		def get(wrapped: Wrapped): Future[Wrapped] =
			elementData(wrapped)
				.map { elements =>
					store(elements)
					wrapped
				}

		def next(wrapped: Wrapped): Future[Uri] = wrapped.next.toFuture

		def matches(wrapped: Wrapped): Future[Wrapped] = {
			wrapped.elements.last.get.similar(collection.last.get)
			Future.successful(wrapped)
		}

		def continue(loc: Uri): Future[Unit] = {
			require(find(loc).isEmpty, s"already seen $loc")
			wrap(loc)
				.flatMap { get }
				.flatMap { next }
				.flatMap { continue }
		}

		def continueAfter(loc: Uri): Future[Unit] =
			wrap(loc)
				.flatMap { matches }
				.flatMap { next }
				.flatMap { continue }

		def start() = collection.last match {
			case None => continue(core.first)
			case Some(last) => continueAfter(last[String]("origin"))
		}

	}

}
