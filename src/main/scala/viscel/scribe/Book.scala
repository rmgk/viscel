package viscel.scribe

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}

import viscel.scribe.Json._
import viscel.shared.Log

import scala.collection.JavaConverters._
import scala.collection.mutable

class Book(path: Path) {

	def add(blob: AppendLogEntry): Unit = {
		Files.write(path, List(upickle.default.write[AppendLogEntry](blob)).asJava, StandardOpenOption.APPEND)
	}

	def emptyArticles(): List[Article] = entries.collect {
		case AppendLogPage(ref, _, contents, _) => contents
	}.flatten.collect {
		case art@Article(ref, _, _) if !blobMap.contains(ref) => art
	}

	def emptyLinks(): List[Link] = entries.collect {
		case AppendLogPage(ref, _, contents, _) => contents
	}.flatten.collect {
		case link@Link(ref, _, _) if !pageMap.contains(ref) => link
	}

	lazy val size: Int = pages().count {
		case ArticleBlob(_, _) => true
		case _ => false
	}

	lazy val name: String = upickle.default.read[String](Files.lines(path).findFirst().get())

	lazy val id: String = path.getFileName.toString

	lazy val entries = Files.lines(path, StandardCharsets.UTF_8).skip(1).iterator.asScala.map { line =>
		upickle.default.read[AppendLogEntry](line)
	}.toList

	lazy val pageMap: Map[Vurl, AppendLogPage] = entries.collect {
		case alp@AppendLogPage(il, _, _, _) => il -> alp
	}.toMap

	lazy val blobMap: Map[Vurl, AppendLogBlob] = entries.collect {
		case alb@AppendLogBlob(il, _, _, _) => il -> alb
	}.toMap

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
