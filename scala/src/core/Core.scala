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

class Clockwork(val iopipe: SendReceive) extends Logging {

	val cores = DCore.list.cores ++ Seq(CarciphonaWrapper, FlipsideWrapper, FreakAngelsWrapper)

	def response(uri: Uri, referer: Option[Uri] = None): Future[HttpResponse] = {
		logger.info(s"get $uri ($referer)")
		val addReferer = referer match {
			case Some(ref) => addHeader("referer", ref.toString)
			case None => (x: HttpRequest) => x
		}
		Get(uri).pipe { addReferer }.pipe { iopipe }
			.flatMap { res => res.validate(_.status.intValue == 200, endRun(s"invalid response ${res.status}; $uri ($referer)")).toFuture }
	}

	def document(uri: Uri): Future[Document] = response(uri).map { res =>
		Jsoup.parse(
			res.entity.asString,
			res.header[Location].map { _.uri }.getOrElse(uri).toString)
	}

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
		wrapped.validate(
			_.elements.forall(_.isSuccess),
			endRun("could not get element: " + {
				wrapped.elements.filter(_.isFailure).map {
					_.failed.get.tap {
						case e: EndRun =>
						case e => e.printStackTrace
					}
				}
			})).toFuture.flatMap {
				_.elements.map(_.get).map { elementData }.pipe { Future.sequence(_) }
			}
	}

	def nonChaptered() = cores.foreach { core =>
		logger.info(s"start core ${core.id}")
		val collection = CollectionNode(core.id).getOrElse(CollectionNode.create(core.id, Some(core.name)))

		new Runner(collection, core.wrapper).start(core.first).onComplete {
			case Success(_) => logger.info("test complete without errors")
			case Failure(e) => e match {
				case e: EndRun => logger.info(s"${core.id} complete ${e}")
				case e => logger.info(s"${core.id} failed ${e}"); e.printStackTrace
			}
		}
	}

	def chaptered() = DrMcNinjaWrapper.pipe { core =>
		new ChapteredRunner(core).start().onComplete {
			case Success(_) => logger.info("test complete without errors")
			case Failure(e) => e match {
				case e: EndRun => logger.info(s"${core.id} complete ${e}")
				case e => logger.info(s"${core.id} failed ${e}"); e.printStackTrace
			}
		}
	}

	def test() = chaptered(); nonChaptered();

	def hashToFilename(h: String): String = (new StringBuilder(h)).insert(2, '/').insert(0, "../cache/").toString

	class Runner(collection: NodeContainer[ElementNode], wrapper: Wrapper) {

		def wrap(loc: Uri): Future[Wrapped] = loc.pipe { document }.map { wrapper }

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

	class ChapteredRunner(core: ChapteredCore) {

		val collection = ChapteredCollectionNode(core.id).getOrElse(ChapteredCollectionNode.create(core.id, Some(core.name)))

		def getChapter(pos: Int, chapter: Chapter) = {
			val cnode = collection(pos).getOrElse(Neo.txs {
				ChapterNode.create(chapter.name)
					.tap { collection.append }
			})
			new Runner(cnode, core.wrapper).start(chapter.first)
		}

		def nextChapter(pos: Int, chapters: List[Chapter]): Future[Unit] = chapters match {
			case c :: cs =>
				getChapter(pos, c).recoverWith {
					case e: EndRun =>
						logger.info(s"${c.name} of ${collection.id} complete ${e}")
						nextChapter(pos + 1, cs)
					case e => Future.failed(e)
				}
			case Nil => Future.failed(endRun("all chapters done"))
		}

		def start() = {
			document(core.first).map { core.wrapChapter }.flatMap { wchap =>
				wchap.validate(_.chapter.forall(_.isSuccess), failRun("could not get element: " + {
					wchap.chapter.filter(_.isFailure).map {
						_.failed.get.tap {
							case e: EndRun =>
							case e => e.printStackTrace
						}
					}
				})).toFuture
			}.flatMap { wchap =>
				nextChapter(1, wchap.chapter.map { _.get }.to[List])
			}
		}

	}

}
