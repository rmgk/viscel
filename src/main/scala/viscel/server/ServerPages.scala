package viscel.server

import spray.http._
import viscel.store.User

import scalatags.Text.{TypedTag, RawFrag}
import scalatags.Text.attrs.{href, rel, `type`, title, src}
import scalatags.Text.tags.{script, link, head, html, body}
import scalatags.Text.implicits.stringAttr
import argonaut.Json
import argonaut.JsonIdentity.ToJsonIdentity

object ServerPages {
	val path_css: String = "/css"
	val path_js: String = "/js"

	val fullHtml: TypedTag[String] =
		html(
			head(
				link(href := path_css, rel := "stylesheet", `type` := MediaTypes.`text/css`.toString()),
				title := "Viscel"),
			body(),
			script(src := path_js),
			script(RawFrag(s"Viscel().main()")))

	val landing: HttpResponse = HttpResponse(entity = HttpEntity(
		ContentType(MediaTypes.`text/html`, HttpCharsets.`UTF-8`),
		"<!DOCTYPE html>" + fullHtml.render))

	def jsonResponse(json: Json): HttpResponse = HttpResponse(entity = HttpEntity(
		ContentType(MediaTypes.`application/json`, HttpCharsets.`UTF-8`),
		json.nospaces))

	def bookmarks(user: User): HttpResponse = jsonResponse(user.bookmarks.asJson)

	def collections() = jsonResponse()


}
