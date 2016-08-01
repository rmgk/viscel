package viscel.scribe.database

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import scala.collection.JavaConverters._
import viscel.scribe.store.Json._
import viscel.scribe.narration.{AppendLogBlob, AppendLogEntry, AppendLogPage, Article, Chapter, Link, ArticleBlob, WebContent, Entry}
import viscel.shared.Log

class Book(path: Path)(implicit r: upickle.default.Reader[AppendLogEntry]) {
	def size(): Int = pages().count{
		case ArticleBlob(_, _) => true
		case _ => false
	}

	lazy val name: String = upickle.default.read[String](Files.lines(path).findFirst().get())

	lazy val id: String = path.getFileName.toString

	def pages(): List[Entry] = {

		val entries = Files.lines(path, StandardCharsets.UTF_8).skip(1).iterator.asScala.map{ line =>
			upickle.default.read[AppendLogEntry](line)
		}.toList

		val size = entries.size

		val pages = new java.util.HashMap[String, AppendLogPage](size)
		val blobs = new java.util.HashMap[String, AppendLogBlob](size)
		entries.foreach {
			case alb@AppendLogBlob(il, rl, _, _) => blobs.put(il.toString, alb)
			case alp@AppendLogPage(il, rl, contents, _) => pages.put(il.toString, alp)
		}

		@scala.annotation.tailrec
		def flatten(remaining: List[WebContent], acc: List[Entry]): List[Entry] = {
			remaining match {
				case Nil => acc.reverse
				case h :: t => h match {
					case Link(loc, policy, data) =>
						pages.get(loc.toString) match {
							case null => flatten(t, acc)
							case alp => {
								pages.remove(loc.toString)
								flatten(alp.contents ::: t, acc)
							}
						}
					case art @ Article(blob, origin, data) =>
						blobs.get(blob.toString) match {
							case null => flatten(t, acc)
							case alb => flatten(t, ArticleBlob(art, alb) :: acc)
						}
					case chap @ Chapter(_) => flatten(t,  chap :: acc)
				}
			}
		}

		val initialPage = pages.get("http://initial.entry")
		if (initialPage == null) {
			Log.warn(s"Book $id was emtpy")
			Nil
		}
		else flatten(initialPage.contents, Nil)

	}

}
