package viscel.server

import akka.http.scaladsl.model._
import io.circe.Encoder
import io.circe.syntax._
import viscel.narration.Narrators
import viscel.scribe.{Article, ArticleRef, Chapter, ReadableContent, Scribe}
import viscel.shared.{ChapterPos, Contents, Description, Gallery, ImageRef}
import viscel.store.User

import scalatags.Text.attrs.{`type`, action, content, href, rel, src, title, value, name => attrname}
import scalatags.Text.implicits.{Tag, stringAttr, stringFrag}
import scalatags.Text.tags.{body, br, form, head, html, input, link, meta, script, a => anchor}
import scalatags.Text.tags2.section
import scalatags.Text.{Modifier, TypedTag}

class ServerPages(scribe: Scribe) {

	def narration(id: String): Option[Contents] = {
		@scala.annotation.tailrec
		def recurse(content: List[ReadableContent], art: List[ImageRef], chap: List[ChapterPos], c: Int): (List[ImageRef], List[ChapterPos]) = {
			content match {
				case Nil => (art, chap)
				case h :: t => {
					h match {
						case Article(ArticleRef(ref, origin, data), blob) =>
							val article = ImageRef(origin = origin.uriString, blob, data)
							recurse(t, article :: art, if (chap.isEmpty) List(ChapterPos("", 0)) else chap, c + 1)
						case Chapter(name) => recurse(t, art, ChapterPos(name, c) :: chap, c)
					}
				}
			}
		}

		val pages = scribe.findPages(id)
		if (pages.isEmpty) None
		else {
			val (articles, chapters) = recurse(pages, Nil, Nil, 0)
			Some(Contents(Gallery.fromSeq(articles.reverse), chapters))
		}
	}

	def narrations(): Set[Description] = {
		val books = scribe.allDescriptions()
		var known = books.map(d => d.id -> d).toMap
		val nars = Narrators.all.map { n =>
			known.get(n.id) match {
				case None => Description(n.id, n.name, 0, unknownNarrator = false)
				case Some(desc) =>
					known = known - n.id
					desc.copy(unknownNarrator = false)
			}
		}
		nars ++ known.values
	}

	val path_css: String = "css"
	val path_js: String = "js"


	def makeHtml(stuff: Modifier*): TypedTag[String] =
		html(
			head(
				title := "Viscel",
				link(href := path_css, rel := "stylesheet", `type` := MediaTypes.`text/css`.toString()),
				meta(attrname := "viewport", content := "width=device-width, initial-scale=1, user-scalable=yes, minimal-ui"))
		)(stuff: _*)

	def htmlResponse(tag: Tag): HttpResponse = HttpResponse(entity = HttpEntity(
		ContentType(MediaTypes.`text/html`, HttpCharsets.`UTF-8`),
		"<!DOCTYPE html>" + tag.render))

	val fullHtml: TypedTag[String] = makeHtml(body("if nothing happens, your javascript does not work"), script(src := path_js))

	val landing: HttpResponse = htmlResponse(fullHtml)

	def jsonResponse[T: Encoder](value: T): HttpResponse = HttpResponse(entity = HttpEntity(
		ContentType(MediaTypes.`application/json`),
		value.asJson.noSpaces))

	def bookmarks(user: User): HttpResponse = jsonResponse(user.bookmarks)

	val toolsPage: TypedTag[String] = makeHtml(body(section(anchor(href := "stop")("stop")),
		section(
		form(action := "import",
			"id: ", input(`type` := "text", attrname := "id"), br,
			"name: ", input(`type` := "text", attrname := "name"), br,
			"path: ", input(`type` := "text", attrname := "path"), br,
			input(`type` := "submit", value := "import"))),
		section(
			form(action := "add",
				"url: ", input(`type` := "text", attrname := "url"), br,
				input(`type` := "submit", value := "add")))
	))

	val toolsResponse = htmlResponse(toolsPage)

}
