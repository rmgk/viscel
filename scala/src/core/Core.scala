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
	def archive: Uri
	def wrapArchive(doc: Document): Future[ArchiveDescription]
	def wrapPage(doc: Document): Future[FullPage]
}

trait ArchiveDescription {
	def chapters: Seq[ChapterDescription]
}

sealed trait ChapterDescription {
	def name: String
}
case class ChapterPointer(name: String, loc: Uri) extends ChapterDescription
case class LinkedChapter(
	name: String,
	first: PageDescription) extends ChapterDescription
case class CompleteChapter(
	name: String,
	pages: Seq[PageDescription]) extends ChapterDescription

sealed trait PageDescription {
	def loc: Uri
}
case class PagePointer(loc: Uri, next: Option[PageDescription] = None) extends PageDescription
// case class SinglePage(
// 	loc: Uri,
// 	elements: Seq[Try[ElementDescription]]) extends PageDescription
case class FullPage(
	loc: Uri,
	next: Option[Try[PageDescription]],
	elements: Seq[ElementDescription]) extends PageDescription

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

	def similar(node: ElementNode) = {
		require(source == Uri(node[String]("source")), "source does not match")
		require(origin == Uri(node[String]("origin")), "origin does not match")
		require(alt == node.get[String]("alt"), "alt does not match")
		require(title == node.get[String]("title"), "title does not match")
		require(width == node.get[Int]("width"), "width does not match")
		require(height == node.get[Int]("height"), "height does not match")
		true
	}
}
