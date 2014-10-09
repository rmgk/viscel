package viscel.server

import spray.http.{ContentType, HttpCharsets, HttpEntity, HttpResponse, MediaTypes}
import viscel.store.Neo

import scalatags.Text.Tag
import scalatags.Text.tags._
import scalatags.Text.implicits.stringAttr
import scalatags.Text.attrs._
import scalatags.Text.Frag

trait HtmlPage extends HtmlPageUtils {

	def response(implicit neo: Neo): HttpResponse = neo.txts(s"create response $Title") {
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
