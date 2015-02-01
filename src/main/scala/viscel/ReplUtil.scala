package viscel

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths, StandardCopyOption, StandardOpenOption}

import akka.actor.ActorSystem
import org.jsoup.nodes.Document
import spray.client.pipelining.SendReceive
import viscel.crawler.RunnerUtil
import viscel.database.{NeoCodec, Neo}
import viscel.database.Implicits.NodeOps
import viscel.narration.{SelectUtil, Metarrator, Narrator}
import viscel.server.ServerPages
import viscel.shared.Story.{Narration, Chapter, Asset}
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

	def export(id: String)(implicit neo: Neo): Unit = {

		val narrationOption = neo.tx { implicit ntx =>
			Books.find(id).map { nar =>
				val list = nar.self.fold(List[List[Story]]())(state => n => (state, NeoCodec.load[Story](n)) match {
					case (s, c@Chapter(_, _)) => List(c) :: s
					case (Nil, a@Asset(_, _, _, _)) => (a :: Chapter("") :: Nil) :: Nil
					case (c :: xs, a@Asset(_, _, _, _)) => (a :: c) :: xs
					case (s, _) => s
				}).map(_.reverse).reverse
				(nar.name, list, nar.narration(true).chapters)
			}
		}

		if (narrationOption.isEmpty) {
			Log.warn(s"$id not found")
			return
		}


		val p = Paths.get("export", id)
		Files.createDirectories(p)

		val html = "<!DOCTYPE html>" + ServerPages.makeHtml(script(src := "narration"), script(RawFrag( s"""Viscel().spore("$id", JSON.stringify(narration))""")))
		val js = getClass.getClassLoader.getResource("viscel-js-opt.js")
		val css = getClass.getClassLoader.getResource("style.css")

		def mimeToExt(mime: String) = mime match {
			case "image/jpeg" => "jpg"
			case "image/gif" => "gif"
			case "image/png" => "png"
			case _ => "bmp"
		}


		val (narname, chapters, flatChapters) = narrationOption.get

		val assetList = chapters.zipWithIndex.flatMap {
			case (chap :: assets, cpos) =>
				val cname = f"$cpos%04d"
				val dir = p.resolve(cname)
				Files.createDirectories(dir)
				assets.zipWithIndex.map {
					case (a@Asset(_, _, _, blob), apos) =>
						val name = f"$apos%05d.${ mimeToExt(a.blob.fold("")(_.mediatype)) }"
						blob.foreach { b =>
							Files.copy(Paths.get(BlobStore.hashToFilename(b.sha1)), dir.resolve(name), StandardCopyOption.REPLACE_EXISTING)
						}
						a.copy(blob = blob.map(b => b.copy(sha1 = s"$cname/$name")))
					case _ => throw new IllegalStateException("invalid archive structure")
				}
			case _ => throw new IllegalStateException("invalid archive structure")
		}

		val assembled = Narration(id, narname, assetList.size, Gallery.fromList(assetList), flatChapters)

		val narJson = "var narration = " + upickle.write(assembled)

		Files.write(p.resolve(s"${ narname }.html"), html.getBytes(StandardCharsets.UTF_8))
		Files.write(p.resolve("narration"), narJson.getBytes(StandardCharsets.UTF_8))
		Files.copy(Paths.get(js.toURI), p.resolve("js"), StandardCopyOption.REPLACE_EXISTING)
		Files.copy(Paths.get(css.toURI), p.resolve("css"), StandardCopyOption.REPLACE_EXISTING)


	}
}
