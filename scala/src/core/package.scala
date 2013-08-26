package viscel

import org.jsoup.select.Elements
import spray.http.HttpHeaders.`Content-Type`
import spray.http.HttpHeaders.Location
import spray.http.HttpRequest
import spray.http.HttpResponse
import spray.http.ContentType
import scala.language.implicitConversions

package core {
	class AbortRun(msg: String) extends Throwable(msg)
	case class ElementData(mediatype: ContentType, sha1: String, buffer: Array[Byte], response: HttpResponse, element: Element)

}

package object core {
	def abort(msg: String) = new AbortRun(msg)
	def found(count: Int, name: String)(es: Elements) = if (es.size == count) true else throw new AbortRun(s"$name found ${es.size} need $count")
}
