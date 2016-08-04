package viscel.scribe

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}

import viscel.scribe.Json._
import viscel.shared.Log

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class Book(path: Path) {

	def add(entry: ScribeDataRow): Unit = {
		Files.write(path, List(upickle.default.write[ScribeDataRow](entry)).asJava, StandardOpenOption.APPEND)
		entry match {
			case alp@ScribePage(il, _, _, _) => pageMap.put(il, alp)
			case alb@ScribeBlob(il, _, _, _) => blobMap.put(il, alb)
		}
		entries += entry
	}

	def emptyArticles(): List[ArticleRef] = entries.collect {
		case ScribePage(ref, _, _, contents) => contents
	}.flatten.collect {
		case art@ArticleRef(ref, _, _) if !blobMap.contains(ref) => art
	}.toList

	def emptyLinks(): List[Link] = entries.collect {
		case ScribePage(ref, _, _, contents) => contents
	}.flatten.collect {
		case link@Link(ref, _, _) if !pageMap.contains(ref) => link
	}.toList

	lazy val size: Int = pages().count {
		case Article(_, _) => true
		case _ => false
	}

	lazy val name: String = upickle.default.read[String](Files.lines(path).findFirst().get())

	lazy val id: String = path.getFileName.toString

	lazy val entries: ArrayBuffer[ScribeDataRow] = {
		Files.lines(path, StandardCharsets.UTF_8).skip(1).iterator.asScala.map { line =>
			upickle.default.read[ScribeDataRow](line)
		}.to[ArrayBuffer]
	}

	lazy val pageMap: mutable.HashMap[Vurl, ScribePage] = {
		val map = mutable.HashMap[Vurl, ScribePage]()
		entries.collect {
			case alp@ScribePage(il, _, _, _) => map.put(il, alp)
		}
		map
	}

	lazy val blobMap: mutable.HashMap[Vurl, ScribeBlob] = {
		val map = mutable.HashMap[Vurl, ScribeBlob]()
		entries.collect {
			case alb@ScribeBlob(il, _, _, _) => map.put(il, alb)
		}
		map
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
				case Nil => acc.reverse
				case h :: t => h match {
					case Link(loc, policy, data) =>
						pageMap.get(loc) match {
							case None => flatten(t, acc)
							case Some(alp) => flatten(unseen(alp.contents) ::: t, acc)
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
				flatten(unseen(initialPage.contents), Nil)
		}

	}

}
