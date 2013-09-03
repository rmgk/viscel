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

trait FullArchiveRunner extends NetworkPrimitives with Logging {

	def core: Core
	def collection: CollectionNode

	def getChapter(lc: LinkedChapter, cn: ChapterNode, fb: Uri => Boolean): Future[Unit] = {
		new ChapterRunner() {
			def chapterNode = cn
			def forbidden = fb
			def pageRunner = new PlaceholderPageRunner() {}.apply(_)
			// new FullPageRunner() {
			// 	def iopipe = FullArchiveRunner.this.iopipe
			// 	def wrapPage = core.wrapPage
			// }.apply(_)
		}.continue(lc.first)
			.recoverWith {
				case e: NormalStatus =>
					logger.info(s"chapter ${cn.name} finished normally: ${e.getMessage}")
					Future.successful(())
			}
	}

	def getChapterList(cl: List[LinkedChapter]): Future[Unit] = cl match {
		case List() => Future.successful(())
		case c :: tcl =>
			logger.info(s"create chapter ${c.name} for ${collection.id}")
			val cnode = ChapterNode.create(c.name)
			collection.append(cnode)
			getChapter(c, cnode, tcl.map { _.first.loc }.toSet)
				.flatMap { _ => getChapterList(tcl) }
	}

	def continue(archive: ArchiveDescription, chapters: List[LinkedChapter] = List()): Future[Unit] = {
		val fullArchive = archive match {
			case fa: FullArchive => Future.successful(fa)
			case ap: ArchivePointer =>
				document(ap.loc)
					.flatMap { core.wrapArchive }
		}
		fullArchive.flatMap { fa =>
			val allChapters = chapters ::: fa.chapters.toList.asInstanceOf[List[LinkedChapter]]
			fa.next match {
				case Some(archive) => continue(archive, allChapters)
				case None => getChapterList(allChapters)
			}
		}
	}

	def start(): Future[Unit] = continue(core.archive)
}

