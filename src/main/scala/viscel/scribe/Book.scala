package viscel.scribe

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}
import java.util.stream.Collectors

import io.circe.syntax._
import viscel.scribe.ScribePicklers._
import viscel.shared.Log
import viscel.store.DescriptionCache

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class Book private (path: Path, descriptionCache: DescriptionCache,
	pageMap: mutable.Map[Vurl, ScribePage],
	blobMap: mutable.Map[Vurl, ScribeBlob],
	entries: ArrayBuffer[ScribeDataRow]) {

	def add(entry: ScribeDataRow): Unit = {
		val index = entries.lastIndexWhere(_.matchesRef(entry))
		if (index < 0 || entries(index).differentContent(entry)) {
			Files.write(path, List(entry.asJson.noSpaces).asJava, StandardOpenOption.APPEND)
			entry match {
				case alp@ScribePage(il, _, _, _) =>
					pageMap.put(il, alp)
					descriptionCache.updateSize(id, alp.articleCount - (if (index < 0) 0 else entries(index).asInstanceOf[ScribePage].articleCount))
				case alb@ScribeBlob(il, _, _, _) => blobMap.put(il, alb)
			}
			if (index >= 0) entries.remove(index)
			entries += entry
		}
	}

	def beginning: Option[ScribePage] = pageMap.get(Vurl.entrypoint)
	def hasPage(ref: Vurl): Boolean = pageMap.contains(ref)
	def hasBlob(ref: Vurl): Boolean = blobMap.contains(ref)

	def export(path: Path): Unit = {
		Files.write(path, List(name).asJava, StandardOpenOption.CREATE_NEW)
		Files.write(path, entries.map(entry => entry.asJson.noSpaces).asJava, StandardOpenOption.APPEND)
	}

	def emptyArticles(): List[ArticleRef] = entries.collect {
		case ScribePage(ref, _, _, contents) => contents
	}.flatten.collect {
		case art@ArticleRef(ref, _, _) if !blobMap.contains(ref) => art
	}.toList

	def volatileAndEmptyLinks(): List[Link] = entries.collect {
		case ScribePage(ref, _, _, contents) => contents
	}.flatten.collect {
		case link@Link(ref, Volatile, _) => link
		case link@Link(ref, _, _) if !pageMap.contains(ref) => link
	}.toList

	def size(): Int = pages().count {
		case Article(_, _) => true
		case _ => false
	}

	def allBlobs(): Iterator[ScribeBlob] = entries.iterator.collect { case sb@ScribeBlob(_, _, _, _) => sb }

	lazy val name: String = io.circe.parser.decode[String](Files.lines(path).findFirst().get()).toTry.get

	lazy val id: String = path.getFileName.toString

	def rightmostScribePages(): List[Link] = {

		val seen = mutable.HashSet[Vurl]()

		@scala.annotation.tailrec
		def rightmost(remaining: ScribePage, acc: List[Link]): List[Link] = {
			val next = remaining.contents.reverseIterator.find {
				case Link(loc, _, _) if seen.add(loc) => true
				case _ => false
			} collect {
				case l@Link(_, _, _) => l
			}
			next match {
				case None => acc
				case Some(link) =>
					pageMap.get(link.ref) match {
						case None => link :: acc
						case Some(scribePage) =>
							rightmost(scribePage, link :: acc)
					}
			}
		}

		pageMap.get(Vurl.entrypoint) match {
			case None =>
				Log.warn(s"Book $id was emtpy")
				Nil
			case Some(initialPage) =>
				rightmost(initialPage, Nil)
		}

	}


	def pages(): List[ReadableContent] = {

		Log.info(s"pages for $id")

		val seen = mutable.HashSet[Vurl]()

		def unseen(contents: List[WebContent]): List[WebContent] = {
			contents.filter {
				case link@Link(loc, policy, data) => seen.add(loc)
				case _ => true
			}
		}

		@scala.annotation.tailrec
		def flatten(remaining: List[WebContent], acc: List[ReadableContent]): List[ReadableContent] = {
			remaining match {
				case Nil => acc
				case h :: t => h match {
					case Link(loc, policy, data) =>
						pageMap.get(loc) match {
							case None => flatten(t, acc)
							case Some(alp) => flatten(unseen(alp.contents) reverse_::: t, acc)
						}
					case art@ArticleRef(ref, origin, data) =>
						val blob = blobMap.get(ref).map(_.blob)
						flatten(t, Article(art, blob) :: acc)
					case chap@Chapter(_) => flatten(t, chap :: acc)
				}
			}
		}

		pageMap.get(Vurl.entrypoint) match {
			case None =>
				Log.warn(s"Book $id was emtpy")
				Nil
			case Some(initialPage) =>
				flatten(unseen(initialPage.contents.reverse), Nil)
		}

	}

}

object Book {
	def load(path: Path, descriptionCache: DescriptionCache) = {
		val pageMap: mutable.HashMap[Vurl, ScribePage] = mutable.HashMap[Vurl, ScribePage]()
		val blobMap: mutable.HashMap[Vurl, ScribeBlob] = mutable.HashMap[Vurl, ScribeBlob]()

		val entries: ArrayBuffer[ScribeDataRow] = {

			def putIfAbsent[A, B](hashMap: mutable.HashMap[A, B], k: A, v: B): Boolean = {
				var res = false
				hashMap.getOrElseUpdate(k, {res = true; v})
				res
			}

			Log.info(s"reading $path")

			val fileStream = Files.lines(path, StandardCharsets.UTF_8)
			try {
				fileStream.skip(1).collect(Collectors.toList()).asScala.view.zipWithIndex.reverseIterator.map { case (line, nr) =>
					io.circe.parser.decode[ScribeDataRow](line) match {
						case Right(s) => s
						case Left(t) =>
							Log.error(s"Failed to decode $path:${nr + 2}: $line")
							throw t
					}
				}.filter {
					case spage@ScribePage(il, _, _, _) => putIfAbsent(pageMap, il, spage)
					case sblob@ScribeBlob(il, _, _, _) => putIfAbsent(blobMap, il, sblob)
				}.to[ArrayBuffer].reverse
			}
			finally {
				fileStream.close()
			}
		}
		new Book(path, descriptionCache, pageMap, blobMap, entries)
	}
}
