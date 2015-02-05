package viscel

import java.nio.charset.StandardCharsets
import java.nio.file.{Path, Paths, Files, StandardCopyOption, StandardOpenOption}
import java.util.Comparator
import java.util.function.Predicate

import akka.actor.ActorSystem
import org.jsoup.nodes.Document
import spray.client.pipelining.SendReceive
import viscel.crawler.{Archive, RunnerUtil}
import viscel.database.{Books, NeoCodec, Neo}
import viscel.database.Implicits.NodeOps
import viscel.narration.{SelectUtil, Metarrator, Narrator}
import viscel.server.ServerPages
import viscel.shared.Story.More.Kind
import viscel.shared.Story.{Content, Description, Chapter, Asset}
import viscel.shared.{Story, Gallery, ViscelUrl}
import viscel.store.BlobStore

import scala.annotation.tailrec
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.collection.JavaConverters.asScalaIteratorConverter
import scala.Predef.augmentString
import scalatags.Text.RawFrag
import scalatags.Text.attrs.src
import scalatags.Text.tags.script
import scalatags.Text.implicits.{stringAttr, stringFrag}
import scala.collection.immutable.Map

class ReplUtil(val system: ActorSystem, val iopipe: SendReceive) {

	def fetch(vurl: ViscelUrl): Document = {
		val request = RunnerUtil.request(vurl)
		val res = RunnerUtil.getResponse(request, iopipe).map { RunnerUtil.parseDocument(vurl) }
		res.onFailure { case t: Throwable =>
			Log.error(s"error fetching $vurl")
			t.printStackTrace()
		}
		Await.result(res, Duration.Inf)
	}

	//	def updateMetarrator[T <: Narrator](metarrator: Metarrator[T]) = {
	//		val doc = fetch(metarrator.archive)
	//		val nars = metarrator.wrap(doc)
	//		metarrator.save(nars.get)
	//	}

	def shutdown() = {
		system.shutdown()
		Viscel.neo.shutdown()
	}
}

object ReplUtil {
	def apply() = {
		val (system, ioHttp, iopipe) = Viscel.run()
		new ReplUtil(system, iopipe)
	}

	def mimeToExt(mime: String, default: String = "") = mime match {
		case "image/jpeg" => "jpg"
		case "image/gif" => "gif"
		case "image/png" => "png"
		case "image/bmp" => "bmp"
		case _ => default
	}

	def export(id: String)(implicit neo: Neo): Unit = {

		val narrationOption = neo.tx { implicit ntx =>
			Books.findExisting(id).map { nar =>
				(nar.name, nar.content())
			}
		}

		if (narrationOption.isEmpty) {
			Log.warn(s"$id not found")
			return
		}


		val p = Viscel.basepath.resolve("export").resolve(id)
		Files.createDirectories(p)


		val (narname, content) = narrationOption.get

		val chapters: List[(Chapter, Seq[Asset])] = content.chapters.foldLeft((content.gallery.size, List[(Chapter, Seq[Asset])]())){ case ((np, cs), (p, c)) =>
			(p, (c, Range(p, np).map(p => content.gallery.next(p).get.get)) :: cs)
		}._2

		val assetList = chapters.zipWithIndex.flatMap {
			case ((chap, assets), cpos) =>
				val cname = f"${cpos + 1}%04d"
				val dir = p.resolve(cname)
				Files.createDirectories(dir)
				assets.zipWithIndex.map {
					case (a, apos) =>
						val name = f"${apos + 1}%05d.${ mimeToExt(a.blob.fold("")(_.mediatype), default = "bmp") }"
						a.blob.foreach { b =>
							Files.copy(Viscel.basepath.resolve(BlobStore.hashToFilename(b.sha1)), dir.resolve(name), StandardCopyOption.REPLACE_EXISTING)
						}
						a.copy(blob = a.blob.map(b => b.copy(sha1 = s"$cname/$name")))
				}
		}


		val assembled = (Description(id, narname, assetList.size), content.copy(Gallery.fromList(assetList)))

		val narJson = "var narration = " + upickle.write(assembled)
		val html = "<!DOCTYPE html>" + ServerPages.makeHtml(script(src := "narration"), script(RawFrag( s"""Viscel().spore("$id", JSON.stringify(narration))""")))


		Files.write(p.resolve(s"${ narname }.html"), html.getBytes(StandardCharsets.UTF_8))
		Files.write(p.resolve("narration"), narJson.getBytes(StandardCharsets.UTF_8))

		val js = getClass.getClassLoader.getResourceAsStream("viscel-js-opt.js")
		val css = getClass.getClassLoader.getResourceAsStream("style.css")

		Files.copy(js, p.resolve("js"), StandardCopyOption.REPLACE_EXISTING)
		Files.copy(css, p.resolve("css"), StandardCopyOption.REPLACE_EXISTING)

	}

	def importFolder(path: String, nid: String, nname: String)(implicit neo: Neo) = {
		import viscel.narration.SelectUtil.stringToVurl
		import scala.math.Ordering.Implicits.seqDerivedOrdering

		Log.info(s"try to import $nid($nname) form $path")

		val files = Files.walk(Paths.get(path))
		val sortedFiles = try { files.iterator().asScala.toList.sortBy(_.iterator().asScala.map(_.toString).toList) }
		finally files.close()
		val story = sortedFiles.flatMap { p =>
			if (Files.isDirectory(p)) Some(Chapter(p.getFileName.toString))
			else if (Files.isRegularFile(p)) {
				val mime = Files.probeContentType(p)
				if (mimeToExt(mime, default = "") == "") None
				else {
					Log.info(s"processing $p")
					val sha1 = BlobStore.write(Files.readAllBytes(p))
					val blob = Story.Blob(sha1, mime)
					Some(Asset(p.toUri.toString, p.getParent.toUri.toString, Map(), Some(blob)))
				}
			}
			else None
		}

		neo.tx { implicit ntx =>

			val book = Books.findAndUpdate(new Narrator {
				override def name: String = nname
				override def archive: List[Story] = Nil
				override def wrap(doc: Document, kind: Kind): List[Story] = Nil
				override def id: String = nid
			})

			Archive.applyNarration(book.self, story)
		}


	}

}
