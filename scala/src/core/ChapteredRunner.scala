// package viscel.core

// import akka.actor.{ ActorSystem, Props, Actor }
// import akka.io.IO
// import com.typesafe.scalalogging.slf4j.Logging
// import org.jsoup.Jsoup
// import org.jsoup.nodes.Document
// import scala.concurrent._
// import scala.concurrent.duration._
// import scala.concurrent.ExecutionContext.Implicits.global
// import scala.util._
// import spray.can.Http
// import spray.client.pipelining._
// import spray.http.Uri
// import viscel._
// import viscel.store._
// import spray.http.HttpHeaders.`Content-Type`
// import spray.http.HttpHeaders.Location
// import spray.http.HttpRequest
// import spray.http.HttpResponse
// import spray.http.ContentType
// import scalax.io._
// import scala.collection.JavaConversions._
// import org.neo4j.graphdb.Direction

// trait ChapteredRunner extends NetworkPrimitives {

// 	def wrapPage: Document => PageDescription
// 	def collection: CollectionNode
// 	def chapters: Seq[ChapterDescription]

// 	def knownChapters = collection.children.sortBy(_.position)
// 	def allKnownUris = knownChapters.flatMap { _.children }.map { en => Uri(en[String]("origin")) }

// 	def getChapter(chapter: ChapterDescription, cnode: ChapterNode, seenUris: Set[Uri]) = {
// 		chapter.first.toFuture.flatMap { first =>
// 			new LinkedPageRunner {
// 				def iopipe = ChapteredRunner.this.iopipe
// 				def wrapPage = ChapteredRunner.this.wrapPage
// 				def chapter = cnode
// 				def forbidden = seenUris
// 			}.continue(first)
// 		}
// 	}

// 	def nextChapter(pos: Int, chapters: List[ChapterDescription]): Future[Unit] = chapters match {
// 		case c :: cs =>
// 			getChapter(c, collection(pos), allKnownUris).recoverWith {
// 				case e: EndRun =>
// 					logger.info(s"${c.name} of ${collection.id} complete ${e}")
// 					nextChapter(pos + 1, cs)
// 				case e => Future.failed(e)
// 			}
// 		case Nil => Future.failed(endRun("all chapters done"))
// 	}

// 	def start(first: Uri) = {
// 		document(first).map { wrapChapter }.flatMap { wchap =>
// 			wchap.validate(_.chapter.forall(_.isSuccess), failRun("could not get element: " + {
// 				wchap.chapter.filter(_.isFailure).map {
// 					_.failed.get.tap {
// 						case e: EndRun =>
// 						case e => e.printStackTrace
// 					}
// 				}
// 			})).toFuture
// 		}.flatMap { wchap =>
// 			nextChapter(1, wchap.chapter.map { _.get }.to[List])
// 		}
// 	}

// }
