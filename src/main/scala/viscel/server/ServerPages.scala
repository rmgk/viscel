package viscel.server

import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.time.{Duration, Instant}

import akka.http.scaladsl.model._
import upickle.default.Writer
import viscel.Log
import viscel.narration.{AssetKind, Data, Narrators}
import viscel.scribe.Scribe
import viscel.scribe.appendstore.{AppendLogArticle, AppendLogBlob, AppendLogChapter, AppendLogElements, AppendLogEntry, AppendLogMore, AppendLogPage}
import viscel.scribe.database.{Neo, label}
import viscel.scribe.narration.{Page, Asset => SAsset}
import viscel.shared.JsonCodecs.stringMapW
import viscel.shared.{Article, Chapter, Content, Description, Gallery}
import viscel.store.User

import scala.collection.immutable.Map
import scalatags.Text.attrs.{`type`, content, href, rel, src, title, name => attrname}
import scalatags.Text.implicits.{Tag, stringAttr, stringFrag}
import scalatags.Text.tags.{body, head, html, link, meta, script}
import scalatags.Text.{Modifier, RawFrag}
import scala.collection.JavaConverters._
import viscel.store.Json._

import scala.collection.mutable


class ServerPages(scribe: Scribe) {

	import scribe.books._

	def neo: Neo = scribe.neo

	object ArticlePage {
		def unapply(page: Page): Option[Article] = page match {
			case Page(SAsset(source, origin, AssetKind.article, data), blob) =>
				Some(Article(
					source = source map (_.toString),
					origin = origin map (_.toString),
					data = Data.listToMap(data),
					blob = blob map (_.sha1),
					mime = blob map (_.mime)))
			case _ => None
		}
	}

	object ChapterPage {
		def unapply(page: Page): Option[String] = page match {
			case Page(SAsset(None, None, AssetKind.chapter, name :: data), None) => Some(name)
			case _ => None
		}
	}

	def appendLogNarration(id: String): Option[Content] = {

		val entries = Files.lines(Paths.get(s"logs/$id"), StandardCharsets.UTF_8).iterator.asScala.map{ line =>
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
		def flatten(remaining: List[AppendLogElements], acc: List[AppendLogElements]): List[AppendLogElements] = {
			remaining match {
				case Nil => acc.reverse
				case h :: t => h match {
					case AppendLogMore(loc, policy, data) =>
						pages.get(loc.toString) match {
							case null => flatten(t, acc)
							case alp => flatten(alp.contents ::: t, acc)
						}
					case other => flatten(t,  other :: acc)
				}
			}
		}

		val elements = flatten(pages.get("http://initial.entry").contents, Nil)

		def recurse(content: List[AppendLogElements], art: List[Article], chap: List[Chapter], c: Int): (List[Article], List[Chapter]) = {
			content match {
				case Nil => (art, chap)
				case h :: t => h match {
					case AppendLogChapter(name) => recurse(t, art, Chapter(name, c) :: chap, c)
					case AppendLogArticle(blob, origin, data) =>
						val article = blobs.get(blob.toString) match {
							case null =>
								Article(Some(blob.toString), origin.map(_.toString))
							case AppendLogBlob(il, rl, sha1, mime, _) =>
								Article(Some(blob.toString), origin.map(_.toString), Some(sha1), Some(mime),  Data.listToMap(data))
						}
						recurse(t, article :: art, if (chap.isEmpty) List(Chapter("", 0)) else chap, c + 1)
					case AppendLogMore(_, _, _) => throw new IllegalStateException("append log mores should already be excluded")
				}
			}
		}

		val (articles, chapters) = recurse(elements, Nil, Nil, 0)

		Some(Content(Gallery.fromList(articles.reverse), chapters))

	}

	def narration(id: String): Option[Content] = neo.tx { implicit ntx =>
		(Narrators.get(id) match {
			case None => findExisting(id)
			case Some(nar) => Some(findAndUpdate(nar))
		}).map(book => {
			val content: (Int, List[Article], List[Chapter]) = book.pages().foldLeft((0, List[Article](), List[Chapter]())) {
				case (state@(pos, assets, chapters), ArticlePage(article)) =>
					(pos + 1, article :: assets, if (chapters.isEmpty) List(Chapter("", 0)) else chapters)
				case (state@(pos, assets, chapters), ChapterPage(name)) =>
					(pos, assets, Chapter(name, pos) :: chapters)
				case (state@(pos, assets, chapters), page) =>
					Log.error(s"unhandled page $page")
					state
			}

			Content(Gallery.fromList(content._2.reverse), content._3)

		})
	}

	def narrations(): HttpResponse =
		jsonResponse {
			val books = neo.tx { implicit ntx =>
				scribe.books.all().map(b => Description(b.id, b.name, b.size(0)))
			}
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

	def stats(): HttpResponse = jsonResponse {
		val cn = scribe.cfg
		scribe.neo.tx { implicit ntx =>
			Map[String, Long](
				"Downloaded" -> cn.downloaded,
				"Downloads" -> cn.downloads,
				"Compressed downloads" -> cn.downloadsCompressed,
				"Failed downloads" -> cn.downloadsFailed,
				"Books" -> ntx.nodes(label.Book).size,
				"Assets" -> ntx.nodes(label.Asset).size)
		}
	}

}
