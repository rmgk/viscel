package viscel.server

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Instant

import akka.http.scaladsl.model._
import upickle.default.Writer
import viscel.Viscel
import viscel.narration.{Narrators}
import viscel.scribe.Scribe
import viscel.scribe.narration.{AppendLogBlob, AppendLogEntry, AppendLogPage, More, Story, Article => SArticle, Chapter => SChapter}
import viscel.scribe.store.Json._
import viscel.shared.{Article, Blob, Chapter, Content, Description, Gallery}
import viscel.store.User

import scala.collection.JavaConverters._
import scalatags.Text.attrs.{`type`, content, href, rel, src, title, name => attrname}
import scalatags.Text.implicits.{Tag, stringAttr, stringFrag}
import scalatags.Text.tags.{body, head, html, link, meta, script}
import scalatags.Text.{Modifier, RawFrag}


class ServerPages(scribe: Scribe) {

	def narration(id: String): Option[Content] = {

		val path = Viscel.basepath.resolve("scribe").resolve("db3").resolve("books")


		val entries = Files.lines(path.resolve(s"$id"), StandardCharsets.UTF_8).skip(1).iterator.asScala.map{ line =>
			upickle.default.read[AppendLogEntry](line)
		}.toList

		val size = entries.size

		val pages = new java.util.HashMap[String, AppendLogPage](size)
		val blobs = new java.util.HashMap[String, AppendLogBlob](size)
		entries.foreach {
			case alb@AppendLogBlob(il, rl, sha1, mime, _) => blobs.put(il.toString, alb)
			case alp@AppendLogPage(il, rl, contents, _) => pages.put(il.toString, alp)
		}

		@scala.annotation.tailrec
		def flatten(remaining: List[Story], acc: List[Story]): List[Story] = {
			remaining match {
				case Nil => acc.reverse
				case h :: t => h match {
					case More(loc, policy, data) =>
						pages.get(loc.toString) match {
							case null => flatten(t, acc)
							case alp => flatten(alp.contents ::: t, acc)
						}
					case other => flatten(t,  other :: acc)
				}
			}
		}

		val elements = flatten(pages.get("http://initial.entry").contents, Nil)

		def recurse(content: List[Story], art: List[Article], chap: List[Chapter], c: Int): (List[Article], List[Chapter]) = {
			content match {
				case Nil => (art, chap)
				case h :: t => h match {
					case SChapter(name) => recurse(t, art, Chapter(name, c) :: chap, c)
					case SArticle(blob, origin, data) =>
						val article = blobs.get(blob.toString) match {
							case null =>
								Article(source = blob.toString, origin = origin.toString)
							case AppendLogBlob(il, rl, sha1, mime, _) =>
								Article(source = blob.toString, origin = origin.toString, Some(Blob(sha1, mime)),  data)
						}
						recurse(t, article :: art, if (chap.isEmpty) List(Chapter("", 0)) else chap, c + 1)
					case More(_, _, _) => throw new IllegalStateException("append log mores should already be excluded")
				}
			}
		}

		val (articles, chapters) = recurse(elements, Nil, Nil, 0)

		Some(Content(Gallery.fromList(articles.reverse), chapters))

	}

	def narrations(): HttpResponse =
		jsonResponse {
			val books = scribe.books.all().map(b => Description(b.id, b.name, b.size()))
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
