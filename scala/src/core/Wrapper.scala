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

trait Definition {
	def id: String
	def name: String
	def first: Uri
	def wrapper: Wrapper
}

trait Wrapper extends (Document => Wrapped) {
	override def apply(document: Document): Wrapped
}

trait Wrapped {
	def document: Document
	def next: Try[Uri]
	def elements: Seq[Try[Element]]
}

object CarciphonaWrapper extends Definition with Wrapper with Logging {
	def id = "X_Carciphona"
	def name = "Carciphona"

	val first = Uri("http://carciphona.com/view.php?page=cover&chapter=1&lang=")
	def wrapper: Wrapper = this

	val extractImageUri = """[\w-]+:url\((.*)\)""".r

	def found(count: Int)(es: Elements) = require(es.size == count, s"wrong number of elements found ${es.size} need $count")

	override def apply(doc: Document): Wrapped = new Wrapped {
		def document = doc
		val next = document.select("#link #nextarea").validate { found(1) }.map { _.attr("abs:href").pipe { Uri.parseAbsolute(_) } }
		val elements = document.select(".page:has(#link)").validate { found(1) }
			.map {
				_.attr("style")
					.pipe { case extractImageUri(img) => img }
					.pipe { Uri.parseAndResolve(_, doc.baseUri) }
					.pipe { uri => Element(source = uri.toString) }
			}.pipe { Seq(_) }
	}

}
