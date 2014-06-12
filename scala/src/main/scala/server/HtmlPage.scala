package viscel.server

import scalatags._
import scalatags.all._
import spray.http.{ HttpResponse, HttpEntity, MediaTypes, ContentType, HttpCharsets }
import viscel.store.Neo
import viscel.store.{ Util => StoreUtil }

trait HtmlPage extends HtmlPageUtils {

	def response: HttpResponse = Neo.txts(s"create response $Title") {
		HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`text/html`, HttpCharsets.`UTF-8`),
			"<!DOCTYPE html>" + fullHtml.toString))
	}

	def fullHtml = html(header, content)

	def header: HtmlTag = head(
		"stylesheet".attr := path_css,
		title := Title)

	def Title: String
	def bodyId: String

	def mainPart: Seq[Node]
	def navigation: Seq[Node]
	def sidePart: Seq[Node]

	def content: Node = body(id := bodyId)(
		div(class_main)(mainPart: _*),
		div(class_navigation)(navigation: _*),
		div(class_side)(sidePart: _*))

}
