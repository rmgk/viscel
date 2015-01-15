package viscel

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths, StandardCopyOption, StandardOpenOption}

import akka.actor.ActorSystem
import org.jsoup.nodes.Document
import spray.client.pipelining.SendReceive
import viscel.crawler.RunnerUtil
import viscel.database.Neo
import viscel.narration.{Metarrator, Narrator}
import viscel.server.ServerPages
import viscel.shared.Story.Asset
import viscel.shared.{Story, Gallery, ViscelUrl}
import viscel.store.{BlobStore, Books}

import scala.annotation.tailrec
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.Predef.augmentString
import scalatags.Text.RawFrag
import scalatags.Text.attrs.src
import scalatags.Text.tags.script
import scalatags.Text.implicits.{stringAttr, stringFrag}

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

	def updateMetarrator[T <: Narrator](metarrator: Metarrator[T]) = {
		val doc = fetch(metarrator.archive)
		val nars = metarrator.wrap(doc)
		metarrator.save(nars.get)
	}

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

	def export(id: String)(implicit neo: Neo): Unit = {
		val p = Paths.get("export", id)
		Files.createDirectories(p)
		val nar = neo.tx { implicit ntx => Books.getNarration(id, deep = true).get }

		val html = "<!DOCTYPE html>" + ServerPages.makeHtml(script(src := "narration"),script(RawFrag(s"""Viscel().spore("$id", JSON.stringify(narration))""")))
		val js = getClass.getClassLoader.getResource("viscel-js-opt.js")
		val css = getClass.getClassLoader.getResource("style.css")

		val pBlob = p.resolve("blob")
		Files.createDirectories(pBlob)

		def mimeToExt(mime: String) = mime match {
			case "image/jpeg" => "jpg"
			case "image/gif" => "gif"
			case "image/png" => "png"
			case _ => "bmp"
		}

		val assetList = nar.narrates.toList.zipWithIndex.map { case (a, pos) =>
			val name = f"$pos%05d.${mimeToExt(a.blob.fold("")(_.mediatype))}"
			a.blob.foreach { b =>
				Files.copy(Paths.get(BlobStore.hashToFilename(b.sha1)), pBlob.resolve(name), StandardCopyOption.REPLACE_EXISTING)
			}
			a.copy(blob = a.blob.map(b => b.copy(sha1 = name)))
		}

		val narJson = "var narration = " + upickle.write(nar.copy(narrates = Gallery.fromList(assetList)))

		Files.write(p.resolve(s"${nar.name}.html"), html.getBytes(StandardCharsets.UTF_8))
		Files.write(p.resolve("narration"), narJson.getBytes(StandardCharsets.UTF_8))
		Files.copy(Paths.get(js.toURI), p.resolve("js"), StandardCopyOption.REPLACE_EXISTING)
		Files.copy(Paths.get(css.toURI), p.resolve("css"), StandardCopyOption.REPLACE_EXISTING)




	}
}
