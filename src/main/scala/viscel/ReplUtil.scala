package viscel

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, StandardCopyOption}

import viscel.scribe.Scribe
import viscel.server.ServerPages
import viscel.shared.{Article, Chapter, Description, Gallery}

import scalatags.Text.RawFrag
import scalatags.Text.attrs.src
import scalatags.Text.implicits.stringAttr
import scalatags.Text.tags.script


class ReplUtil(scribe: Scribe) {
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


		val p = Viscel.basepath.resolve("export").resolve(id)
		Files.createDirectories(p)


		val content = narrationOption.get
		val description = scribe.neo.tx { implicit nxt =>
			val b = scribe.books.findExisting(id).get
			Description(b.id, b.name, b.size)
		}

		val chapters: List[(Chapter, Seq[Article])] =
			content.chapters.foldLeft((content.gallery.size, List[(Chapter, Seq[Article])]())) {
				case ((nextPosition, chapters), chapter@Chapter(name, position)) =>
					(position, (chapter, Range(position, nextPosition).map(p => content.gallery.next(p).get.get)) :: chapters)
			}._2

		val assetList = chapters.zipWithIndex.flatMap {
			case ((chap, articles), cpos) =>
				val cname = f"${ cpos + 1 }%04d"
				val dir = p.resolve(cname)
				Files.createDirectories(dir)
				articles.zipWithIndex.map {
					case (a, apos) =>
						val name = f"${ apos + 1 }%05d.${ mimeToExt(a.mime.getOrElse(""), default = "bmp") }"
						a.blob.foreach { sha1 =>
							Files.copy(scribe.blobs.hashToPath(sha1), dir.resolve(name), StandardCopyOption.REPLACE_EXISTING)
						}
						a.copy(blob = a.blob.map(_ => s"$cname/$name"))
				}
		}


		val assembled = (description, content.copy(Gallery.fromList(assetList)))

		val narJson = "var narration = " + upickle.write(assembled)
		val html = "<!DOCTYPE html>" + pages.makeHtml(script(src := "narration"), script(RawFrag( s"""Viscel().spore("$id", JSON.stringify(narration))""")))


		Files.write(p.resolve(s"${ description.id }.html"), html.getBytes(StandardCharsets.UTF_8))
		Files.write(p.resolve("narration"), narJson.getBytes(StandardCharsets.UTF_8))

		val js = getClass.getClassLoader.getResourceAsStream("viscel-js-opt.js")
		val css = getClass.getClassLoader.getResourceAsStream("style.css")

		Files.copy(js, p.resolve("js"), StandardCopyOption.REPLACE_EXISTING)
		Files.copy(css, p.resolve("css"), StandardCopyOption.REPLACE_EXISTING)

	}

	//	def importFolder(path: String, nid: String, nname: String)(implicit neo: Neo) = {
	//		import viscel.narration.SelectUtil.stringToVurl
	//
	//		import scala.math.Ordering.Implicits.seqDerivedOrdering
	//
	//		Log.info(s"try to import $nid($nname) form $path")
	//
	//		val files = Files.walk(Paths.get(path))
	//		val sortedFiles = try { files.iterator().asScala.toList.sortBy(_.iterator().asScala.map(_.toString).toList) }
	//		finally files.close()
	//		val story = sortedFiles.flatMap { p =>
	//			if (Files.isDirectory(p)) Some(Chapter(p.getFileName.toString))
	//			else if (Files.isRegularFile(p)) {
	//				val mime = Files.probeContentType(p)
	//				if (mimeToExt(mime, default = "") == "") None
	//				else {
	//					Log.info(s"processing $p")
	//					val sha1 = BlobStore.write(Files.readAllBytes(p))
	//					val blob = Story.Blob(sha1, mime)
	//					Some(Asset(p.toUri.toString, p.getParent.toUri.toString, Map(), Some(blob)))
	//				}
	//			}
	//			else None
	//		}
	//
	//		neo.tx { implicit ntx =>
	//
	//			val book = Books.findAndUpdate(new Narrator {
	//				override def name: String = nname
	//				override def archive: List[Story] = Nil
	//				override def wrap(doc: Document, kind: Kind): List[Story] = Nil
	//				override def id: String = nid
	//			})
	//
	//			Archive.applyNarration(book.self, story)
	//		}
	//
	//
	//	}

}
