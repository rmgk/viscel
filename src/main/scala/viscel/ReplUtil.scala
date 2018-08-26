package viscel

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths, StandardCopyOption}
import java.time.Instant

import io.circe.generic.auto._
import io.circe.syntax._
import org.jsoup.nodes.Document
import org.scalactic.{Every, Or}
import scalatags.Text.RawFrag
import scalatags.Text.attrs.src
import scalatags.Text.implicits.stringAttr
import scalatags.Text.tags.script
import viscel.narration.Narrator
import viscel.scribe.{Article, BlobData, Chapter, ImageRef, Link, PageData, ReadableContent, Vurl, WebContent}
import viscel.selection.Report
import viscel.shared.{Blob, ChapterPos, Description, Gallery, SharedImage, Vid}
import viscel.store.BlobStore

import scala.collection.JavaConverters.asScalaIteratorConverter
import scala.collection.immutable.HashSet
import scala.collection.mutable

class ReplUtil(services: Services) {

  val Log = viscel.shared.Log.Tool

  def mimeToExt(mime: String, default: String = ""): String = mime match {
    case "image/jpeg" => "jpg"
    case "image/gif" => "gif"
    case "image/png" => "png"
    case "image/bmp" => "bmp"
    case _ => default
  }

  def export(id: Vid): Unit = {

    val narrationOption = services.contentLoader.narration(id)

    if (narrationOption.isEmpty) {
      Log.warn(s"$id not found")
      return
    }


    val p = services.exportdir.resolve(id.str)
    Files.createDirectories(p)


    val content = narrationOption.get
    val description: Description = ???

    val chapters: List[(ChapterPos, Seq[SharedImage])] =
      content.chapters.foldLeft((content.gallery.size, List[(ChapterPos, Seq[SharedImage])]())) {
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
              Files.copy(services.blobs.hashToPath(blob.sha1), dir.resolve(name), StandardCopyOption.REPLACE_EXISTING)
              blob.copy(sha1 = s"$cname/$name")
            })
        }
    }


    val assembled = (description, content.copy(Gallery.fromSeq(assetList)))

    val narJson = "var narration = " + assembled.asJson.noSpaces
    val html = "<!DOCTYPE html>" + services.serverPages.makeHtml(script(src := "narration"), script(RawFrag(s"""Viscel().spore("$id", JSON.stringify(narration))""")))


    Files.write(p.resolve(s"${description.id}.html"), html.getBytes(StandardCharsets.UTF_8))
    Files.write(p.resolve("narration"), narJson.getBytes(StandardCharsets.UTF_8))

    val js = getClass.getClassLoader.getResourceAsStream("viscel-js-opt.js")
    val css = getClass.getClassLoader.getResourceAsStream("style.css")

    Files.copy(js, p.resolve("js"), StandardCopyOption.REPLACE_EXISTING)
    Files.copy(css, p.resolve("css"), StandardCopyOption.REPLACE_EXISTING)

  }

  def importFolder(path: String, nid: Vid, nname: String): Unit = {

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
          val sha1 = services.blobs.write(Files.readAllBytes(p))
          val blob = Blob(sha1, mime)
          Some(Article(ImageRef(Vurl.blobPlaceholder(blob), Vurl.blobPlaceholder(blob)), Some(blob)))
        }
      }
      else None
    }

    val webcont = story.collect {
      case chap@Chapter(_) => chap
      case Article(ar, _) => ar
    }

    val blobs = story.collect {
      case Article(ar, Some(blob)) => BlobData(ar.ref, ar.origin, Instant.now(), blob)
    }

    val narrator = new Narrator {
      override def id: Vid = nid
      override def name: String = nname
      override def archive: List[WebContent] = ???
      override def wrap(doc: Document, more: Link): Or[List[WebContent], Every[Report]] = ???
    }
    val id = narrator.id
    val appender = services.rowStore.open(narrator)
    appender.append(PageData(Vurl.entrypoint, Vurl.entrypoint, Instant.now(), webcont))
    blobs.foreach(appender.append)
    services.descriptionCache.invalidate(id)


  }


  def cleanBlobDirectory(): Unit = {
    Log.info(s"scanning all blobs …")
    val blobsHashesInDB = {
      services.rowStore.allVids().flatMap { id =>
        val book = services.rowStore.load(id)
        book.allBlobs().map(_.blob.sha1)
      }.to[HashSet]
    }
    Log.info(s"scanning files …")
    val bsn = new BlobStore(services.basepath.resolve("blobbackup"))

    val seen = mutable.HashSet[String]()

    Files.walk(services.blobdir).iterator().asScala.filter(Files.isRegularFile(_)).foreach { bp =>
      val sha1path = s"${bp.getName(bp.getNameCount - 2)}${bp.getFileName}"
      //val sha1 = blobs.sha1hex(Files.readAllBytes(bp))
      //if (sha1path != sha1) Log.warn(s"$sha1path did not match")
      seen.add(sha1path)
      if (!blobsHashesInDB.contains(sha1path)) {
        val newpath = bsn.hashToPath(sha1path)
        Log.info(s"moving $bp to $newpath")
        Files.createDirectories(newpath.getParent)
        Files.move(bp, newpath)
      }
    }
    blobsHashesInDB.diff(seen).foreach(sha1 => Log.info(s"$sha1 is missing"))
  }


}
