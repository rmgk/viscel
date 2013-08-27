package viscel.core

import com.typesafe.scalalogging.slf4j.Logging
import scala.concurrent.ExecutionContext.Implicits.global
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import scala.concurrent._
import scala.util._
import spray.client.pipelining._
import spray.http.Uri
import viscel._
import scala.collection.JavaConversions._

trait Core {
	def id: String
	def name: String
	def first: Uri
	def wrapper: Wrapper
}

trait ChapteredCore extends Core {
	def wrapChapter(doc: Document): WrappedChapter
}

trait Wrapper extends (Document => Wrapped) {
	override def apply(document: Document): Wrapped
}

trait Wrapped {
	def document: Document
	def next: Try[Uri]
	def elements: Seq[Try[Element]]
}

trait WrappedChapter {
	def document: Document
	def chapter: Seq[Try[Chapter]]
}
