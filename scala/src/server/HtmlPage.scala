package viscel.server

import com.typesafe.scalalogging.slf4j.Logging
import scala.xml.Elem
import scala.xml.Node
import scala.xml.NodeSeq
import scalatags._
import spray.http.{ HttpResponse, HttpEntity, MediaTypes, ContentType, HttpCharsets }
import viscel.store.CollectionNode
import viscel.store.ElementNode
import viscel.store.Neo
import viscel.store.UserNode
import viscel.store.ViscelNode
import viscel.store.{ Util => StoreUtil }
import viscel.time

trait HtmlPage extends HtmlPageUtils {

	def response: HttpResponse = Neo.txts(s"create response $Title") {
		HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`text/html`, HttpCharsets.`UTF-8`),
			"<!DOCTYPE html>" + fullHtml.toXML.toString))
	}

	def fullHtml = html(header, content)

	def header = head(
		stylesheet(path_css),
		title(Title))

	def Title: String
	def bodyId: String

	def mainPart: STag
	def navigation: STag
	def sidePart: STag

	def content: STag = body.id(bodyId)(
		div.cls("main")(mainPart),
		div.cls("navigation")(navigation),
		div.cls("side")(sidePart))

}
