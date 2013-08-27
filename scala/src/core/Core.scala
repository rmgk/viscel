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
	def first: Uri
	def wrapPage(doc: Document): WrappedPage
}

trait ChapteredCore extends Core {
	def wrapChapter(doc: Document): WrappedChapter
}

trait WrappedPage {
	def next: Try[Uri]
	def elements: Seq[Try[Element]]
}

trait WrappedChapter {
	def chapter: Seq[Try[Chapter]]
}

class EndRun(msg: String) extends Throwable(msg)

case class Chapter(name: String, first: Uri)

case class ElementData(mediatype: ContentType, sha1: String, buffer: Array[Byte], response: HttpResponse, element: Element)

case class Element(
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
