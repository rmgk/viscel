package viscel.scribe

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import viscel.shared.Log

import scala.collection.JavaConverters._
import scala.collection.mutable

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

		val pages = mutable.HashMap[Vurl, AppendLogPage]()
		val blobs = mutable.HashMap[Vurl, AppendLogBlob]()
		entries.foreach {
			case alb@AppendLogBlob(il, rl, _, _) => blobs.put(il, alb)
			case alp@AppendLogPage(il, rl, contents, _) => pages.put(il, alp)
		}

		@scala.annotation.tailrec
		def flatten(remaining: List[WebContent], acc: List[Entry]): List[Entry] = {
			remaining match {
				case Nil => acc.reverse
				case h :: t => h match {
					case Link(loc, policy, data) =>
						pages.get(loc) match {
							case None => flatten(t, acc)
							case Some(alp) =>
								pages.remove(loc)
								flatten(alp.contents ::: t, acc)
						}
					case art @ Article(blob, origin, data) =>
						blobs.get(blob) match {
							case None => flatten(t, acc)
							case Some(alb) => flatten(t, ArticleBlob(art, alb) :: acc)
						}
					case chap @ Chapter(_) => flatten(t,  chap :: acc)
				}
			}
		}

		pages.get(Vurl.entrypoint) match {
			case None =>
				Log.warn(s"Book $id was emtpy")
				Nil
			case Some(initialPage) =>
				flatten(initialPage.contents, Nil)
		}

	}

}
