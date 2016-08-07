package viscel

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths, StandardCopyOption}
import java.time.Instant

import org.jsoup.nodes.Document
import org.scalactic.{Every, Or}
import viscel.narration.Narrator
import viscel.scribe.{Article, ArticleRef, Chapter, Link, ReadableContent, Scribe, ScribeBlob, ScribePage, Vurl, WebContent}
import viscel.selection.Report
import viscel.server.ServerPages
import viscel.shared.{Blob, ChapterPos, Description, Gallery, ImageRef, Log}
import viscel.store.BlobStore

import scala.collection.JavaConverters.asScalaIteratorConverter
import scalatags.Text.RawFrag
import scalatags.Text.attrs.src
import scalatags.Text.implicits.stringAttr
import scalatags.Text.tags.script

class ReplUtil(scribe: Scribe, blobstore: BlobStore) {
	def mimeToExt(mime: String, default: String = "") = mime match {
		case "image/jpeg" => "jpg"
		case "image/gif" => "gif"
		case "image/png" => "png"
		case "image/bmp" => "bmp"
		case _ => default
	}

	def export(id: String): Unit = {

		val pages = new ServerPages(scribe)

		val narrationOption = pages.narration(id)

		if (narrationOption.isEmpty) {
			Log.warn(s"$id not found")
			return
		}


		val p = Viscel.exportdir.resolve(id)
		Files.createDirectories(p)


		val content = narrationOption.get
		val description: Description = ???

		val chapters: List[(ChapterPos, Seq[ImageRef])] =
			content.chapters.foldLeft((content.gallery.size, List[(ChapterPos, Seq[ImageRef])]())) {
				case ((nextPosition, chapters), chapter@ChapterPos(name, position)) =>
					(position, (chapter, Range(position, nextPosition).map(p => content.gallery.next(p).get.get)) :: chapters)
			}._2

		val assetList = chapters.zipWithIndex.flatMap {
			case ((chap, articles), cpos) =>
				val cname = f"${cpos + 1}%04d"
				val dir = p.resolve(cname)
				Files.createDirectories(dir)
				articles.zipWithIndex.map {
					case (a, apos) =>
						a.copy(blob = a.blob.map { blob =>
							val name = f"${apos + 1}%05d.${mimeToExt(blob.mime, default = "bmp")}"
							Files.copy(blobstore.hashToPath(blob.sha1), dir.resolve(name), StandardCopyOption.REPLACE_EXISTING)
							blob.copy(sha1 = s"$cname/$name")
						})
				}
		}


		val assembled = (description, content.copy(Gallery.fromList(assetList)))

		val narJson = "var narration = " + upickle.default.write(assembled)
		val html = "<!DOCTYPE html>" + pages.makeHtml(script(src := "narration"), script(RawFrag( s"""Viscel().spore("$id", JSON.stringify(narration))""")))


		Files.write(p.resolve(s"${description.id}.html"), html.getBytes(StandardCharsets.UTF_8))
		Files.write(p.resolve("narration"), narJson.getBytes(StandardCharsets.UTF_8))

		val js = getClass.getClassLoader.getResourceAsStream("viscel-js-opt.js")
		val css = getClass.getClassLoader.getResourceAsStream("style.css")

		Files.copy(js, p.resolve("js"), StandardCopyOption.REPLACE_EXISTING)
		Files.copy(css, p.resolve("css"), StandardCopyOption.REPLACE_EXISTING)

	}

	def importFolder(path: String, nid: String, nname: String): Unit = {

		import scala.math.Ordering.Implicits.seqDerivedOrdering

		Log.info(s"try to import $nid($nname) form $path")

		val files = Files.walk(Paths.get(path))
		val sortedFiles = try {files.iterator().asScala.toList.sortBy(_.iterator().asScala.map(_.toString).toList)}
		finally files.close()
		val story: List[ReadableContent] = sortedFiles.flatMap { p =>
			if (Files.isDirectory(p)) Some(Chapter(name = p.getFileName.toString))
			else if (Files.isRegularFile(p)) {
				val mime = Files.probeContentType(p)
				if (mimeToExt(mime, default = "") == "") None
				else {
					Log.info(s"processing $p")
					val sha1 = blobstore.write(Files.readAllBytes(p))
					val blob = Blob(sha1, mime)
					Some(Article(ArticleRef(Vurl.blobPlaceholder(blob), Vurl.blobPlaceholder(blob)), Some(blob)))
				}
			}
			else None
		}

		val webcont = story.collect {
			case chap@Chapter(_) => chap
			case Article(ar, _) => ar
		}

		val blobs = story.collect {
			case Article(ar, Some(blob)) => ScribeBlob(ar.ref, ar.origin, Instant.now(), blob)
		}

		val narrator = new Narrator {
			override def id: String = nid
			override def name: String = nname
			override def archive: List[WebContent] = ???
			override def wrap(doc: Document, more: Link): Or[List[WebContent], Every[Report]] = ???
		}
		val book = scribe.findOrCreate(narrator)
		book.add(ScribePage(Vurl.entrypoint, Vurl.entrypoint, Instant.now(), webcont))
		blobs.foreach(book.add)


	}

}
