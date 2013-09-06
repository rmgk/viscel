package viscel.core

import com.typesafe.scalalogging.slf4j.Logging
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import org.neo4j.graphdb.Node
import scala.collection.JavaConversions._
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util._
import spray.client.pipelining._
import spray.http.Uri
import viscel._
import viscel.store.ElementNode
import org.jsoup.select.Elements
import spray.http.HttpHeaders.`Content-Type`
import spray.http.HttpHeaders.Location
import spray.http.HttpRequest
import spray.http.HttpResponse
import spray.http.ContentType
import scala.language.implicitConversions

trait Core {
	def id: String
	def name: String
	def archive: ArchiveDescription
	def wrapArchive(doc: Document): Try[FullArchive]
	def wrapPage(doc: Document): Try[FullPage]
}

sealed trait ArchiveDescription
case class ArchivePointer(loc: Uri, next: Option[ArchiveDescription] = None) extends ArchiveDescription
case class FullArchive(chapters: Seq[ChapterDescription], next: Option[ArchiveDescription] = None) extends ArchiveDescription

sealed trait ChapterDescription { def name: String; def props: Map[String, String] }
case class ChapterPointer(name: String, loc: Uri, props: Map[String, String] = Map()) extends ChapterDescription
case class LinkedChapter(name: String, first: PageDescription, props: Map[String, String] = Map()) extends ChapterDescription

sealed trait PageDescription { def loc: Uri; def props: Map[String, String] }
case class PagePointer(loc: Uri, next: Option[PageDescription] = None, props: Map[String, String] = Map()) extends PageDescription
case class FullPage(loc: Uri, next: Try[PageDescription], elements: Seq[ElementDescription], props: Map[String, String] = Map()) extends PageDescription

case class ElementData(mediatype: ContentType, sha1: String, buffer: Array[Byte], response: HttpResponse, description: ElementDescription)

case class ElementDescription(
	source: Uri,
	origin: Uri,
	alt: Option[String] = None,
	title: Option[String] = None,
	width: Option[Int] = None,
	height: Option[Int] = None) {

	def toMap = {
		Map("source" -> source.toString,
			"origin" -> origin.toString) ++
			alt.map { "alt" -> _ } ++
			title.map { "title" -> _ } ++
			width.map { "width" -> _ } ++
			height.map { "height" -> _ }
	}
}
