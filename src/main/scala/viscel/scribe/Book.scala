package viscel.scribe

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}

import viscel.scribe.Json._
import viscel.shared.Log

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class Book(path: Path) {

	def add(entry: AppendLogEntry): Unit = {
		Files.write(path, List(upickle.default.write[AppendLogEntry](entry)).asJava, StandardOpenOption.APPEND)
		entry match {
			case alp@AppendLogPage(il, _, _, _) => pageMap.put(il, alp)
			case alb@AppendLogBlob(il, _, _, _) => blobMap.put(il, alb)
		}
		entries += entry
	}

	def emptyArticles(): List[Article] = entries.collect {
		case AppendLogPage(ref, _, _, contents) => contents
	}.flatten.collect {
		case art@Article(ref, _, _) if !blobMap.contains(ref) => art
	}.toList

	def emptyLinks(): List[Link] = entries.collect {
		case AppendLogPage(ref, _, _, contents) => contents
	}.flatten.collect {
		case link@Link(ref, _, _) if !pageMap.contains(ref) => link
	}.toList

	lazy val size: Int = pages().count {
		case ArticleBlob(_, _) => true
		case _ => false
	}

	lazy val name: String = upickle.default.read[String](Files.lines(path).findFirst().get())

	lazy val id: String = path.getFileName.toString

	lazy val entries: ArrayBuffer[AppendLogEntry] = {
		Files.lines(path, StandardCharsets.UTF_8).skip(1).iterator.asScala.map { line =>
			upickle.default.read[AppendLogEntry](line)
		}.to[ArrayBuffer]
	}

	lazy val pageMap: mutable.HashMap[Vurl, AppendLogPage] = {
		val map = mutable.HashMap[Vurl, AppendLogPage]()
		entries.collect {
			case alp@AppendLogPage(il, _, _, _) => map.put(il, alp)
		}
		map
	}

	lazy val blobMap: mutable.HashMap[Vurl, AppendLogBlob] = {
		val map = mutable.HashMap[Vurl, AppendLogBlob]()
		entries.collect {
			case alb@AppendLogBlob(il, _, _, _) =>  map.put(il, alb)
		}
		map
	}

	def pages(): List[Entry] = {

		Log.info(s"pages for $id")

		val seen = mutable.HashSet[Vurl]()

		@scala.annotation.tailrec
		def flatten(remaining: List[WebContent], acc: List[Entry]): List[Entry] = {
			remaining match {
				case Nil => acc.reverse
				case h :: t => h match {
					case Link(loc, policy, data) =>
						if (seen.contains(loc)) {
							flatten(t, acc)
						}
						else {
							seen += loc
							pageMap.get(loc) match {
								case None => flatten(t, acc)
								case Some(alp) => flatten(alp.contents ::: t, acc)
							}
						}
					case art@Article(blob, origin, data) =>
						blobMap.get(blob) match {
							case None => flatten(t, acc)
							case Some(alb) => flatten(t, ArticleBlob(art, alb) :: acc)
						}
					case chap@Chapter(_) => flatten(t, chap :: acc)
				}
			}
		}

		pageMap.get(Vurl.entrypoint) match {
			case None =>
				Log.warn(s"Book $id was emtpy")
				Nil
			case Some(initialPage) =>
				flatten(initialPage.contents, Nil)
		}

	}

}
