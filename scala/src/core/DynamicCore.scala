package viscel.core

import com.typesafe.scalalogging.slf4j.Logging
import scala.concurrent.ExecutionContext.Implicits.global
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import org.jsoup.nodes.{ Element => SoupElement }
import scala.concurrent._
import scala.util._
import spray.client.pipelining._
import spray.http.Uri
import viscel._
import scala.collection.JavaConversions._
import scala.language.dynamics

object DCore {
	def apply(cores: List[DynamicCore] = List()) = new DynamicCoreBuilder(cores)

	def list = apply()
		.Twokinds(
			"Twokinds",
			"http://twokinds.keenspot.com/archive.php?p=1",
			"#cg_img")

}

class DynamicCoreBuilder(val cores: List[DynamicCore]) extends Dynamic {
	def applyDynamic(id: String)(name: String, first: Uri, img: String, next: String = null) =
		new DynamicCoreBuilder(new DynamicCore(s"DX_$id", name, first, img, Option(next)) :: cores)
}

class DynamicCore(val id: String, val name: String, val first: Uri, elementSelector: String, nextSelector: Option[String]) extends Core with Logging {

	def wrapper = this

	def wrapPage(doc: Document): WrappedPage = new DynamicWrapped(doc, elementSelector, nextSelector)

}

class DynamicWrapped(document: Document, elementSelector: String, nextSelector: Option[String]) extends WrappedPage {

	val img = document.select(elementSelector).select("img").validate { found(1, "image") }

	def strOpt(s: String) = if (s.isEmpty) None else Some(s)

	def imgToElement(img: SoupElement): Element = Element(
		source = img.attr("abs:src").pipe { Uri.parseAbsolute(_) },
		origin = img.baseUri,
		alt = strOpt(img.attr("alt")),
		title = strOpt(img.attr("title")),
		width = strOpt(img.attr("width")).map { _.toInt },
		height = strOpt(img.attr("height")).map { _.toInt })

	val next = nextSelector.pipe {
		case None => img.flatMap {
			_.parents.find { _.tag.getName == "a" }.pipe {
				case Some(t) => Try(t)
				case None => Try { throw endRun("image has no anchor parent") }
			}
		}
		case Some(nextSelector) => document.select(nextSelector).validate { found(1, "next") }.map { _.get(0) }
	}.map { _.attr("abs:href").pipe { Uri.parseAbsolute(_) } }

	val elements: Seq[Try[Element]] = img.map { _.get(0) }.map { imgToElement }.pipe(Seq(_))

}
