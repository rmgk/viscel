package viscel.server.pages

import spray.http.{ContentType, HttpCharsets, HttpEntity, HttpResponse, MediaTypes}
import viscel.store.Neo

import scalatags.Text.Tag
import scalatags.Text.all._

trait HtmlPage extends HtmlPageUtils {

	def response: HttpResponse = Neo.txts(s"create response $Title") {
		HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`text/html`, HttpCharsets.`UTF-8`),
			"<!DOCTYPE html>" + fullHtml.toString))
	}

	def fullHtml: Tag = html(header, content)

	def header: Tag = head(
		link(href := path_css, rel := "stylesheet", `type` := MediaTypes.`text/css`.toString()),
		title := Title)

	def Title: String

	def bodyId: String

	def mainPart: Seq[Frag]

	def navigation: Seq[Frag]

	def sidePart: Seq[Frag]

	def content: Tag = body(id := bodyId)(
		div(class_main)(mainPart: _*),
		div(class_navigation)(navigation: _*),
		div(class_side)(sidePart: _*))

}
