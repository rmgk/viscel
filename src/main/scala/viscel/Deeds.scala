package viscel

import org.scalactic.ErrorMessage
import spray.http.HttpResponse
import viscel.narration.NarratorV1

import scala.util.Try

object Deeds {
	var narratorHint: (NarratorV1, Boolean) => Unit = (n, b) => ()
	var responses: Try[HttpResponse] => Unit = r => ()
	var jobResult: List[ErrorMessage] => Unit = e => ()
}
