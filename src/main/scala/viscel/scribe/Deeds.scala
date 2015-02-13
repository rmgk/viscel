package viscel.scribe

import spray.http.HttpResponse

import scala.util.Try

object Deeds {
	var responses: Try[HttpResponse] => Unit = r => ()
}
