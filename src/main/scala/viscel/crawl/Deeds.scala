package viscel.crawl

import org.scalactic.ErrorMessage
import spray.http.HttpResponse
import viscel.crawl.narration.Narrator

import scala.util.Try

object Deeds {
	var narratorHint: (Narrator, Boolean) => Unit = (n, b) => ()
	var responses: Try[HttpResponse] => Unit = r => ()
	var jobResult: List[ErrorMessage] => Unit = e => ()
}
