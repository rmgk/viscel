package viscel.core

import com.typesafe.scalalogging.slf4j.Logging
import scala.concurrent.ExecutionContext.Implicits.global
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import scala.concurrent._
import scala.util._
import spray.client.pipelining._
import spray.http.Uri
import viscel._
import scala.collection.JavaConversions._

object InverlochArchive extends Core with Logging {
	def archive = ArchivePointer("http://inverloch.seraph-inn.com/volume1.html")
	def id: String = "AX_Inverloch"
	def name: String = "Inverloch"
	def wrapArchive(doc: Document): Future[FullArchive] =
		doc.getElementById("main").validate(_ != null, FailRun("main id not found")).map { main =>
			val vol = main.child(0).text
			val chapters = main.children.slice(1, 6)
			val cdescs = chapters.flatMap { chapter =>
				val cname = chapter.ownText
				val scenes = chapter.getElementsByTag("a")
				scenes.map { scene =>
					val sname = scene.text
					LinkedChapter(s"$vol; $cname; $sname", PagePointer(scene.attr("abs:href")))
				}
			}
			val volumes = doc.select("#nav > ul > li.lisub > a")
			val nVolInd = volumes.indexWhere { _.text == vol } + 1
			val nextVol = if (1 until volumes.size contains nVolInd) Some(volumes(nVolInd)) else None
			FullArchive(cdescs, nextVol.map { _.attr("abs:href") }.map { ArchivePointer(_) })
		}.toFuture

	def strOpt(s: String) = if (s.isEmpty) None else Some(s)

	def imgToElement(img: Element): ElementDescription = ElementDescription(
		source = img.attr("abs:src").pipe { Uri.parseAbsolute(_) },
		origin = img.baseUri,
		alt = strOpt(img.attr("alt")),
		title = strOpt(img.attr("title")),
		width = strOpt(img.attr("width")).map { _.toInt },
		height = strOpt(img.attr("height")).map { _.toInt })

	def wrapPage(doc: Document): Future[FullPage] =
		doc.select("#main").validate(_.size == 1, FailRun("no image found ${doc.baseUri}")).map { main =>
			val ed = main.select("> p > img").map { imgToElement }
			val next = Try { main(0).getElementsContainingOwnText("Next").attr("abs:href").pipe { Uri.parseAbsolute(_) }.pipe { PagePointer(_) } }
			FullPage(loc = doc.baseUri, elements = ed, next = Some(next))
		}.toFuture
}

object TwokindsArchive extends Core with Logging {
	def archive = ArchivePointer("http://twokinds.keenspot.com/?pageid=3")
	def id: String = "AX_Twokinds"
	def name: String = "Twokinds"

	def wrapArchive(doc: Document): Future[FullArchive] = {

		def extractChapterDescription(element: Element) = for {
			name <- element.select("h4").validate(_.size == 1).map { _.text }
			pageAnchors <- element.select("a").validate(_.size > 0)
			pageUris = pageAnchors.map { _.attr("abs:href") }
			pageDesc = pageUris.foldRight(None: Option[PagePointer]) {
				case (uri, prev) =>
					Some(PagePointer(uri, prev))
			}
		} yield LinkedChapter(name, pageDesc.get)

		Try { doc.select(".chapter").map { extractChapterDescription }.map { _.get } }
			.map { FullArchive(_) }.toFuture
	}

	def strOpt(s: String) = if (s.isEmpty) None else Some(s)

	def imgToElement(img: Element): ElementDescription = ElementDescription(
		source = img.attr("abs:src").pipe { Uri.parseAbsolute(_) },
		origin = img.baseUri,
		alt = strOpt(img.attr("alt")),
		title = strOpt(img.attr("title")),
		width = strOpt(img.attr("width")).map { _.toInt },
		height = strOpt(img.attr("height")).map { _.toInt })

	def wrapPage(doc: Document): Future[FullPage] =
		doc.select("#cg_img img").validate(_.size == 1, FailRun("no image found ${doc.baseUri}")).map { img =>
			val ed = img.map { imgToElement }
			val next = Try { img(0).parent.attr("abs:href").pipe { Uri.parseAbsolute(_) }.pipe { PagePointer(_) } }
			FullPage(loc = doc.baseUri, elements = ed, next = Some(next))
		}.toFuture
}

// object CarciphonaWrapper extends Core with Logging {
// 	def id = "X_Carciphona"
// 	def name = "Carciphona"

// 	val first = Uri("http://carciphona.com/view.php?page=cover&chapter=1&lang=")

// 	val extractImageUri = """[\w-]+:url\((.*)\)""".r

// 	def wrapPage(doc: Document): WrappedPage = new WrappedPage {
// 		def document = doc
// 		val next = document.select("#link #nextarea").validate { found(1, "next") }.map { _.attr("abs:href").pipe { Uri.parseAbsolute(_) } }
// 		val elements = document.select(".page:has(#link)").validate { found(1, "image") }
// 			.map {
// 				_.attr("style")
// 					.pipe { case extractImageUri(img) => img }
// 					.pipe { Uri.parseAndResolve(_, doc.baseUri) }
// 					.pipe { uri => Element(source = uri, origin = doc.baseUri) }
// 			}.pipe { Seq(_) }
// 	}

// }

// object FlipsideWrapper extends Core with Logging {
// 	def id = "X_Flipside"
// 	def name = "Flipside"

// 	val first = Uri("http://flipside.keenspot.com/comic.php?i=1")

// 	def wrapPage(doc: Document): WrappedPage = new WrappedPage {
// 		def document = doc
// 		val next = document.select("[rel=next][accesskey=n]").validate { found(1, "next") }.map { _.attr("abs:href").pipe { Uri.parseAbsolute(_) } }
// 		val elements = document.select("img.ksc").validate { found(1, "image") }.map { itag =>
// 			Element(source = itag.attr("abs:src"), origin = doc.baseUri, alt = Option(itag.attr("alt")))
// 		}.pipe { Seq(_) }
// 	}

// }

// object DrMcNinjaWrapper extends ChapteredCore with Logging {
// 	def id = "XC_DrMcNinja"
// 	def name = "Dr. McNinja"

// 	val first = Uri("http://drmcninja.com/issues/")

// 	def wrapChapter(doc: Document): WrappedChapter = new WrappedChapter {
// 		def document = doc

// 		def chapter: Seq[Try[Chapter]] = doc.select("#column .serieslist-content > h2 > a").map { href =>
// 			Try(Chapter(
// 				name = href.text,
// 				first = href.attr("abs:href").pipe { Uri.parseAbsolute(_) }))
// 		}
// 	}

// 	val extractChapter = """http://drmcninja.com/archives/comic/(\d+)p\d+/""".r

// 	def wrapPage(doc: Document): WrappedPage = new WrappedPage {
// 		def document = doc
// 		val next = document.select("#comic-head .next").validate { found(1, "next") }.map { _.attr("abs:href") }
// 			//.recoverWith { case e => Try { doc.baseUri.pipe { case extractChapter(c) => s"http://drmcninja.com/archives/comic/${c.toInt + 1}p1/" } } }
// 			.map { Uri.parseAbsolute(_) }
// 		val elements = document.select("#comic img").validate { found(1, "image") }.map { itag =>
// 			Element(source = Uri.parseAbsolute(itag.attr("abs:src")), origin = doc.baseUri, alt = Option(itag.attr("alt")), title = Option(itag.attr("title")))
// 		}.pipe { Seq(_) }
// 	}

// }

// object FreakAngelsWrapper extends Core with Logging {
// 	def id = "X_FreakAngels"
// 	def name = "Freak Angels"

// 	val first = Uri("http://www.freakangels.com/?p=22&page=1")

// 	def wrapPage(doc: Document): WrappedPage = new WrappedPage {
// 		def document = doc
// 		val next = document.select(".pagenums").validate { found(1, "next") }.flatMap { pns =>
// 			val nextid = pns.get(0).ownText.toInt + 1
// 			pns.select(s":containsOwn($nextid)").validate { found(1, "next") }
// 		}.recoverWith { case e => document.select("a[rel=next]").validate { found(1, "next") } }
// 			.map { _.attr { "abs:href" }.pipe { Uri.parseAbsolute(_) } }
// 		val elements = document.select(".entry_page > p > img").map { itag =>
// 			Element(source = Uri.parseAbsolute(itag.attr("abs:src")), origin = doc.baseUri, alt = Option(itag.attr("alt")), title = Option(itag.attr("title")))
// 		}.map { Try(_) }
// 	}

// }
