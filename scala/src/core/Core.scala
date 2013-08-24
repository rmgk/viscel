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

case class ElementData(mediatype: ContentType, sha1: String, buffer: Array[Byte], response: HttpResponse, element: Element)

class Clockwork(val iopipe: SendReceive) extends Logging {

	val cores = Seq(CarciphonaWrapper)

	def get(uri: Uri, referer: Option[Uri] = None): Future[HttpResponse] = {
		logger.info(s"get $uri ($referer)")
		val addReferer = referer match {
			case Some(ref) => addHeader("referer", ref.toString)
			case None => (x: HttpRequest) => x
		}
		Get(uri).pipe { addReferer }.pipe { iopipe }
	}

	def document(uri: Uri): Future[Document] = get(uri).map { res => Jsoup.parse(res.entity.asString, uri.toString) }

	def elementData(eseed: Element): Future[ElementData] = {
		get(eseed.source, Some(eseed.origin)).map { res =>
			ElementData(
				mediatype = res.header[`Content-Type`].get.contentType,
				buffer = res.entity.buffer,
				sha1 = sha1hex(res.entity.buffer),
				response = res,
				element = eseed)
		}
	}

	def wrap(loc: Uri, wrapper: Wrapper): Future[Wrapped] = loc.pipe { document }.map { wrapper }

	def test() = {
		val cp = cores.head
		new Runner(cp).start().onComplete {
			case Success(_) => logger.info("test complete without errorrs")
			case Failure(e) => logger.info(s"test complete ${e.getMessage}")
		}
	}

	def hashToFilename(h: String): String = (new StringBuilder(h)).insert(2, '/').insert(0, "../cache/").toString

	class Runner(core: Core) {
		val collection = CollectionNode(core.id).getOrElse(CollectionNode.create(core.id, Some(core.name)))

		def wrapNext(loc: Uri, wrapper: Wrapper): Future[Unit] = wrap(loc, wrapper).flatMap { wrapped =>
			require(wrapped.elements.forall(_.isSuccess), "not all elements were a success " + (wrapped.elements.filter(_.isFailure).mkString(" & ")))
			wrapped.elements.map(_.get).map { elementData }.pipe { Future.sequence(_) }
				.andThen {
					case Success(elements) =>
						elements.foreach { element =>
							Resource.fromFile(hashToFilename(element.sha1)).write(element.buffer)
							Neo.txs {
								ElementNode.create((element.element.toMap ++ Seq("blob" -> element.sha1, "mediatype" -> element.mediatype.toString)).toSeq: _*)
									.pipe(collection.append(_, None))
							}
						}
				}
				.flatMap { _ =>
					wrapped.next match {
						case Success(n) => wrapNext(n, wrapper)
						case Failure(e) => Future.failed(e)
					}
				}
		}

		def start() = wrapNext(core.first, core.wrapper)

	}

}
