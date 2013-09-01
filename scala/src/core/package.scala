package viscel

import org.jsoup.select.Elements
import spray.http.HttpHeaders.`Content-Type`
import spray.http.HttpHeaders.Location
import spray.http.HttpRequest
import spray.http.HttpResponse
import spray.http.ContentType
import scala.language.implicitConversions

package object core {
	def endRun(msg: String) = new EndRun(msg)
	def failRun(msg: String) = new Throwable(msg)
	def found(count: Int, name: String)(es: Elements) = if (es.size == count) true else throw new EndRun(s"$name found ${es.size} need $count")
}
