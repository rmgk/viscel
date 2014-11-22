package viscel.serverStaticPages

import spray.http.{ContentType, HttpCharsets, HttpEntity, HttpResponse, MediaTypes}
import viscel.database.{Neo, Ntx}

import scalatags.Text.{Frag, Tag}
import scalatags.Text.attrs._
import scalatags.Text.implicits.stringAttr
import scalatags.Text.tags._

abstract class HtmlPage(implicit ntx: Ntx) extends HtmlPageUtils {

	def response: HttpResponse =
		HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`text/html`, HttpCharsets.`UTF-8`),
			"<!DOCTYPE html>" + fullHtml.toString))

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
