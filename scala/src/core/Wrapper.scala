package viscel.core

import spray.client.pipelining._
import scala.concurrent._
import ExecutionContext.Implicits.global
import spray.http.Uri
import com.typesafe.scalalogging.slf4j.Logging
import org.jsoup.nodes.Document
import org.jsoup.select.Elements

import scala.util._
import viscel._

class CarciphonaWrapper(location: Uri, document: Document) extends Logging {

	val extractImageUri = """[\w-]+:url\((.*)\)""".r

	def next: Try[Uri] =
		document.select("#link #nextarea").pipe { es =>
			if (es.size != 1) "wrong number of elements found ${es.size}".fail
			else Success(es)
		}.map { _.attr("abs:href").pipe { Uri.parseAbsolute(_) } }

	def elements: Seq[Try[Element]] =
		document.select(".page:has(#link)").attr("style")
			.pipe {
				case extractImageUri(img) => Success(img)
				case _ => "did not find node".fail
			}.map { Uri.parseAndResolve(_, location) }
			.map { uri => Element(source = uri.toString) }
			.pipe { Seq(_) }

}
