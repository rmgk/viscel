package viscel.server

import spray.http.{ContentType, HttpCharsets, HttpEntity, HttpResponse, MediaTypes}
import viscel.store.Neo

import scalatags._
import scalatags.all._

trait HtmlPage extends HtmlPageUtils {

	def response: HttpResponse = Neo.txts(s"create response $Title") {
		HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`text/html`, HttpCharsets.`UTF-8`),
			"<!DOCTYPE html>" + fullHtml.toString))
	}

	def fullHtml = html(header, content)

	def header: HtmlTag = head(
		link(href := path_css, rel := "stylesheet", `type` := MediaTypes.`text/css`.toString()),
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
