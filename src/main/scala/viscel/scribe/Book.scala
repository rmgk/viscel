package viscel.scribe

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}
import java.util.stream.Collectors

import viscel.scribe.ScribePicklers._
import viscel.shared.Log

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class Book(path: Path, scribe: Scribe) {

	def add(entry: ScribeDataRow): Unit = {
		Files.write(path, List(upickle.default.write[ScribeDataRow](entry)).asJava, StandardOpenOption.APPEND)
		entry match {
			case alp@ScribePage(il, _, _, _) => pageMap.put(il, alp)
			case alb@ScribeBlob(il, _, _, _) => blobMap.put(il, alb)
		}
		val index = entries.indexWhere(_.matches(entry))
		if (index >= 0) entries.remove(index)
		entries += entry
		scribe.invalidateSize(this)
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

	lazy val name: String = upickle.default.read[String](Files.lines(path).findFirst().get())

	lazy val id: String = path.getFileName.toString


	val pageMap: mutable.HashMap[Vurl, ScribePage] = mutable.HashMap[Vurl, ScribePage]()
	val blobMap: mutable.HashMap[Vurl, ScribeBlob] = mutable.HashMap[Vurl, ScribeBlob]()

	private val entries: ArrayBuffer[ScribeDataRow] = {

		def putIfAbsent[A, B](hashMap: mutable.HashMap[A, B], k: A, v: B): Boolean = {
			var res = false
			hashMap.getOrElseUpdate(k, {res = true; v})
			res
		}

		Files.lines(path, StandardCharsets.UTF_8).skip(1).collect(Collectors.toList()).asScala.reverseIterator.map { line =>
			upickle.default.read[ScribeDataRow](line)
		}.filter {
			case spage@ScribePage(il, _, _, _) => putIfAbsent(pageMap, il, spage)
			case sblob@ScribeBlob(il, _, _, _) => putIfAbsent(blobMap, il, sblob)
		}.to[ArrayBuffer].reverse
	}

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
							case Some(alp) => flatten(unseen(alp.contents) reverse_:::  t, acc)
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
