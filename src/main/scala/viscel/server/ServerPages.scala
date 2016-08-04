package viscel.server

import akka.http.scaladsl.model._
import upickle.default.Writer
import viscel.narration.Narrators
import viscel.scribe.{ReadableContent, Scribe, ArticleRef, Article, Chapter}
import viscel.shared.{ImageRef, ChapterPos, Contents, Description, Gallery}
import viscel.store.User

import scalatags.Text.attrs.{`type`, content, href, rel, src, title, name => attrname}
import scalatags.Text.implicits.{Tag, stringAttr, stringFrag}
import scalatags.Text.tags.{body, head, html, link, meta, script}
import scalatags.Text.{Modifier, RawFrag}


class ServerPages(scribe: Scribe) {

	def narration(id: String): Option[Contents] = {


		@scala.annotation.tailrec
		def recurse(content: List[ReadableContent], art: List[ImageRef], chap: List[ChapterPos], c: Int): (List[ImageRef], List[ChapterPos]) = {
			content match {
				case Nil => (art, chap)
				case h :: t => {
					h match {
						case Article(ArticleRef(ref, origin, data), blob) =>
							val article = ImageRef(origin = origin.toString, Some(blob), data)
							recurse(t, article :: art, if (chap.isEmpty) List(ChapterPos("", 0)) else chap, c + 1)
						case Chapter(name) => recurse(t, art, ChapterPos(name, c) :: chap, c)
					}
				}
			}
		}

		scribe.find(id).map { book =>

			val (articles, chapters) = recurse(book.pages(), Nil, Nil, 0)

			Contents(Gallery.fromList(articles.reverse), chapters)
		}

	}

	def narrations(): HttpResponse =
		jsonResponse {
			val books = scribe.all().map(b => Description(b.id, b.name, b.size))
			val known = books.map(_.id).toSet
			val nars = Narrators.all.filterNot(n => known.contains(n.id)).map(n => Description(n.id, n.name, 0))
			nars.toList reverse_::: books
		}


	val path_css: String = "css"
	val path_js: String = "js"


	def makeHtml(stuff: Modifier*): Tag =
		html(
			head(
				title := "Viscel",
				link(href := path_css, rel := "stylesheet", `type` := MediaTypes.`text/css`.toString()),
				meta(attrname := "viewport", content := "width=device-width, initial-scale=1, user-scalable=yes, minimal-ui")),

			body("if nothing happens, your javascript does not work"),
			script(src := path_js)
		)(stuff: _*)


	val fullHtml: Tag = makeHtml(script(RawFrag(s"Viscel().main()")))

	val landing: HttpResponse = HttpResponse(entity = HttpEntity(
		ContentType(MediaTypes.`text/html`, HttpCharsets.`UTF-8`),
		"<!DOCTYPE html>" + fullHtml.render))

	def jsonResponse[T: Writer](value: T): HttpResponse = HttpResponse(entity = HttpEntity(
		ContentType(MediaTypes.`application/json`),
		upickle.default.write(value)))

	def bookmarks(user: User): HttpResponse = jsonResponse(user.bookmarks)

	def stats(): HttpResponse = ???

}
