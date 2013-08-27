package viscel.core

import com.typesafe.scalalogging.slf4j.Logging
import scala.concurrent.ExecutionContext.Implicits.global
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import scala.concurrent._
import scala.util._
import spray.client.pipelining._
import spray.http.Uri
import viscel._
import scala.collection.JavaConversions._

object CarciphonaWrapper extends Core with Wrapper with Logging {
	def id = "X_Carciphona"
	def name = "Carciphona"

	val first = Uri("http://carciphona.com/view.php?page=cover&chapter=1&lang=")
	// val first = Uri("http://carciphona.com/view.php?page=45&chapter=10&lang")
	def wrapper: Wrapper = this

	val extractImageUri = """[\w-]+:url\((.*)\)""".r

	override def apply(doc: Document): Wrapped = new Wrapped {
		def document = doc
		val next = document.select("#link #nextarea").validate { found(1, "next") }.map { _.attr("abs:href").pipe { Uri.parseAbsolute(_) } }
		val elements = document.select(".page:has(#link)").validate { found(1, "image") }
			.map {
				_.attr("style")
					.pipe { case extractImageUri(img) => img }
					.pipe { Uri.parseAndResolve(_, doc.baseUri) }
					.pipe { uri => Element(source = uri, origin = doc.baseUri) }
			}.pipe { Seq(_) }
	}

}

object FlipsideWrapper extends Core with Wrapper with Logging {
	def id = "X_Flipside"
	def name = "Flipside"

	val first = Uri("http://flipside.keenspot.com/comic.php?i=1")
	def wrapper: Wrapper = this

	override def apply(doc: Document): Wrapped = new Wrapped {
		def document = doc
		val next = document.select("[rel=next][accesskey=n]").validate { found(1, "next") }.map { _.attr("abs:href").pipe { Uri.parseAbsolute(_) } }
		val elements = document.select("img.ksc").validate { found(1, "image") }.map { itag =>
			Element(source = itag.attr("abs:src"), origin = doc.baseUri, alt = Option(itag.attr("alt")))
		}.pipe { Seq(_) }
	}

}

object DrMcNinjaWrapper extends ChapteredCore with Wrapper with Logging {
	def id = "XC_DrMcNinja"
	def name = "Dr. McNinja"

	val first = Uri("http://drmcninja.com/issues/")
	def wrapper: Wrapper = this

	def wrapChapter(doc: Document): WrappedChapter = new WrappedChapter {
		def document = doc

		def chapter: Seq[Try[Chapter]] = doc.select("#column .serieslist-content > h2 > a").map { href =>
			Try(Chapter(
				name = href.text,
				first = href.attr("abs:href").pipe { Uri.parseAbsolute(_) }))
		}
	}

	val extractChapter = """http://drmcninja.com/archives/comic/(\d+)p\d+/""".r

	override def apply(doc: Document): Wrapped = new Wrapped {
		def document = doc
		val next = document.select("#comic-head .next").validate { found(1, "next") }.map { _.attr("abs:href") }
			//.recoverWith { case e => Try { doc.baseUri.pipe { case extractChapter(c) => s"http://drmcninja.com/archives/comic/${c.toInt + 1}p1/" } } }
			.map { Uri.parseAbsolute(_) }
		val elements = document.select("#comic img").validate { found(1, "image") }.map { itag =>
			Element(source = Uri.parseAbsolute(itag.attr("abs:src")), origin = doc.baseUri, alt = Option(itag.attr("alt")), title = Option(itag.attr("title")))
		}.pipe { Seq(_) }
	}

}

object FreakAngelsWrapper extends Core with Wrapper with Logging {
	def id = "X_FreakAngels"
	def name = "Freak Angels"

	val first = Uri("http://www.freakangels.com/?p=22&page=1")
	def wrapper: Wrapper = this

	override def apply(doc: Document): Wrapped = new Wrapped {
		def document = doc
		val next = document.select(".pagenums").validate { found(1, "next") }.flatMap { pns =>
			val nextid = pns.get(0).ownText.toInt + 1
			pns.select(s":containsOwn($nextid)").validate { found(1, "next") }
		}.recoverWith { case e => document.select("a[rel=next]").validate { found(1, "next") } }
			.map { _.attr { "abs:href" }.pipe { Uri.parseAbsolute(_) } }
		val elements = document.select(".entry_page > p > img").map { itag =>
			Element(source = Uri.parseAbsolute(itag.attr("abs:src")), origin = doc.baseUri, alt = Option(itag.attr("alt")), title = Option(itag.attr("title")))
		}.map { Try(_) }
	}

}
