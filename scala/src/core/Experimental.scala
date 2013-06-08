package viscel.core

import spray.client.pipelining._
import org.htmlcleaner._
import scala.concurrent._
import ExecutionContext.Implicits.global
import spray.http.Uri
import com.typesafe.scalalogging.slf4j.Logging
import org.jsoup.nodes._

class Experimental extends Logging {

	val first = Uri("http://carciphona.com/view.php?page=33&chapter=4&lang=")
	lazy val cleaner = new HtmlCleaner();

	val extractImageUri = """[\w-]+:url\((.*)\)""".r

	def mount(document: Document) = {
		val linkarea = document.getElementById("link")
		val extractImageUri(img) = linkarea.parent.attr("style")
		val absimg = Uri.parseAndResolve(img, first)
		val next = linkarea.getElementById("nextarea").attr("abs:href")
		logger.info(s"img: $absimg, next: $next")
		(next, absimg)
	}

}
