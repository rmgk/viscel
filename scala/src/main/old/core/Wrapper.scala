package viscel.core

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import scala.collection.JavaConversions._
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.implicitConversions
import scala.util._
import spray.client.pipelining._
import spray.http.Uri
import com.github.theon.uri.{ Uri => Suri }
import viscel._

trait WrapperTools extends StrictLogging {
	implicit def absuri(uri: String): Uri = Uri.parseAbsolute(Suri.parse(uri).toString)

	def getAttr(e: Element, k: String): Option[(String, String)] = {
		val res = e.attr(k)
		if (res.isEmpty) None else Some(k -> res)
	}

	def imgToElement(img: Element): ElementDescription = ElementDescription(
		source = img.attr("abs:src").pipe { absuri },
		origin = img.baseUri,
		props = (getAttr(img, "alt") ++
			getAttr(img, "title") ++
			getAttr(img, "width") ++
			getAttr(img, "height")).toMap)

	def selectNext(from: Element, query: String) =
		from.select(query).validate(_.size > 0, EndRun(s"no next found ${from.baseUri}"))
			.flatMap(_.validate(_.size < 2, FailRun(s"multiple next found ${from.baseUri}")))
			.map { _.attr("abs:href").pipe { absuri }.pipe { PagePointer(_) } }

	def urisToPagePointer(links: Seq[String]): PagePointer = links.map { absuri }
		.foldRight(None: Option[PagePointer]) {
			case (uri, prev) =>
				Some(PagePointer(uri, prev))
		}.get

	def selectUnique(from: Element, query: String) = from.select(query)
		.validate(_.size < 2, FailRun(s"query not unique ($query) on (${from.baseUri})"))
		.flatMap { _.validate(_.size > 0, FailRun(s"query not found ($query) on (${from.baseUri})")) }.map { _(0) }

	def findParentTag(from: Element, tagname: String): Option[Element] =
		if (from.tag.getName === tagname) Some(from)
		else from.parents.find(_.tag.getName === tagname)
}

object Misfile extends Core with WrapperTools with StrictLogging {
	def archive = ArchivePointer("http://www.misfile.com/archives.php?arc=1&displaymode=wide")
	def id: String = "AX_Misfile"
	def name: String = "Misfile"

	def wrapArchive(doc: Document): Try[FullArchive] = Try {
		val chapters = doc.select("#comicbody a:matchesOwn(^Book #\\d+$)").map { anchor => LinkedChapter(first = PagePointer(anchor.attr("abs:href")), name = anchor.ownText) }
		FullArchive(LinkedChapter(name = "Book #1", first = PagePointer(doc.baseUri)) +: chapters)
	}

	def wrapPage(doc: Document): Try[FullPage] =
		selectUnique(doc, ".comiclist table.wide_gallery").map { clist =>
			val elements = clist.select("[id~=^comic_\\d+$] .picture a").map { anchor =>
				ElementDescription(origin = anchor.attr("abs:href"), source = anchor.select("img").attr("abs:src").replace("/t", "/"))
			}
			val next = Try { PagePointer(doc.select("a.next")(0).attr("abs:href")) }.recoverWith {
				case e: IndexOutOfBoundsException => Try(throw EndRun("no next found"))
			}
			FullPage(loc = doc.baseUri, elements = elements, next = (next))
		}
}

object SpyingWithLana extends Core with WrapperTools with StrictLogging {
	def archive = ArchivePointer("http://www.amazingartbros.com/Webcomics")
	def id: String = "AX_SpyingWithLana"
	def name: String = "Spying With Lana"
	def wrapArchive(doc: Document): Try[FullArchive] = Try {
		val links = doc.select("a[href~=Lana(Flare)?\\d+\\.htm").map(e => absuri(e.attr("abs:href").dropRight(4))).reverse
		val chapters = links.zipWithIndex.map { case (l, i) => LinkedChapter(i.toString, PagePointer(l)) }
		FullArchive(chapters)
	}

	def wrapPage(doc: Document): Try[FullPage] = Try {
		val ed = doc.select("img[src^=http://www.amazingartbros.com/Artnew/][width~=\\d\\d\\d]").map(imgToElement)
		val next = selectNext(doc, "a:contains(next page)").map { pp => pp.copy(loc = pp.loc.toString.dropRight(4)) }
		FullPage(loc = doc.baseUri, elements = ed, next = (next))
	}
}

object AmazingAgentLuna extends Core with WrapperTools with StrictLogging {
	def archive = ArchivePointer("http://www.amazingagentluna.com/archive/volume1")
	def id: String = "AX_AmazingAgentLuna"
	def name: String = "Amazing Agent Luna"
	def wrapArchive(doc: Document): Try[FullArchive] =
		selectUnique(doc, "#article-columns").map { columns =>
			val vol = columns.parent.select("h1")(0).ownText
			val elements = columns.select(".archive-summary a").map { anchor =>
				val img = anchor.child(0)
				val imgSrc = img.attr("abs:src").replace("/comics/tn/", "/comics/")
				ElementDescription(origin = anchor.attr("abs:href"), source = imgSrc,
					props = Map("alt" -> img.attr("alt"), "title" -> anchor.text))
			}
			val firstPage = elements.sortBy(_.source.toString).foldRight(Try(throw EndRun("last page")): Try[FullPage]) {
				case (element, prev) =>
					Try(FullPage(loc = element.origin, next = prev, elements = Seq(element)))
			}.get

			val chapter = LinkedChapter(vol, firstPage)

			val nextVol = selectUnique(doc, s"#main a:containsOwn($vol)").toOption.flatMap { n =>
				Option(n.nextElementSibling)
			}.map { _.attr("abs:href") }

			FullArchive(Seq(chapter), nextVol.map { ArchivePointer(_) })
		}

	def wrapPage(doc: Document): Try[FullPage] = ???
}

object FreakAngels extends Core with WrapperTools with StrictLogging {
	def id = "X_FreakAngels"
	def name = "Freak Angels"
	def archive = ArchivePointer("http://www.freakangels.com/")

	def wrapArchive(doc: Document): Try[FullArchive] =
		doc.select(".archive_dropdown option[value~=http://www.freakangels.com/\\?p=\\d+]")
			.validate(_.size > 0, FailRun("episodes not found")).map { episodes =>
				val chapters = episodes.map { ep => LinkedChapter(ep.text, PagePointer(absuri(ep.attr("abs:value")))) }.reverse
				FullArchive(chapters)
			}

	def wrapPage(doc: Document): Try[FullPage] = {
		val itag = selectUnique(doc, ".entry_page > p > img")
		val next = selectUnique(doc, ".pagenums").flatMap { pns =>
			val nextid = pns.ownText.toInt + 1
			selectNext(pns, s":containsOwn($nextid)")
		}.recoverWith { case f => Try(throw EndRun(s"no next: (${f})")) }
		Try { FullPage(loc = doc.baseUri, elements = itag.map { imgToElement }.toOption.toSeq, next = next) }
	}
}

object Avengelyne extends Core with WrapperTools with StrictLogging {
	def archive = ArchivePointer("http://avengelyne.keenspot.com/archive.html")
	def id: String = "AX_Avengelyne"
	def name: String = "Avengelyne"
	def wrapArchive(doc: Document): Try[FullArchive] =
		doc.select("#comicspot > table").validate(_.size === 1, FailRun("main id not found")).map { maintable =>
			val months = maintable.select("table.ks_calendar")
			val chapters = months.map { month =>
				val mname = month.select("td.ks_bigcal_title").text.trim
				val pages = month.select("a").map { _.attr("abs:href") }
				LinkedChapter(mname, urisToPagePointer(pages))
			}

			FullArchive(chapters)
		}

	def wrapPage(doc: Document): Try[FullPage] =
		doc.select("#comicspot img.ksc").validate(_.size === 1, FailRun(s"no image found ${doc.baseUri}")).map { img =>
			FullPage(loc = doc.baseUri, elements = img.map { imgToElement }, next = (selectNext(doc, "a[rel=next]:has(#nexty)")))
		}
}

object PhoenixRequiem extends Core with WrapperTools with StrictLogging {
	def archive = ArchivePointer("http://requiem.seraph-inn.com/archives.html")
	def id: String = "AX_PhoenixRequiem"
	def name: String = "Phoenix Requiem"
	def wrapArchive(doc: Document): Try[FullArchive] =
		doc.select(".main table").validate(_.size === 1, FailRun("main id not found")).map { maintable =>
			val volumes = maintable.select("tr:matches(Volume|Chapter)").grouped(6).toSeq
			val chapters = volumes.flatMap { volumeGroup =>
				val vname = volumeGroup(0).text
				volumeGroup.drop(1).map { cgroup =>
					val cname = cgroup.child(0).text
					val pages = cgroup.child(1).select("a").map { _.attr("abs:href") }
					LinkedChapter(cname, urisToPagePointer(pages), Map("Volume" -> vname))
				}
			}

			FullArchive(chapters)
		}

	def wrapPage(doc: Document): Try[FullPage] =
		doc.select(".main img[alt=Page][src~=pages/\\d+\\.\\w+]").validate(_.size === 1, EndRun(s"no image found ${doc.baseUri}")).map { img =>
			FullPage(loc = doc.baseUri, elements = img.map { imgToElement }, next = (selectNext(img(0).parent, "a")))
		}
}

object MarryMe extends Core with WrapperTools with StrictLogging {
	def archive = FullArchive(Seq(LinkedChapter("Main Story", PagePointer("http://marryme.keenspot.com/d/20120730.html"))))
	def id: String = "AX_MarryMe"
	def name: String = "Marry Me"
	def wrapArchive(doc: Document): Try[FullArchive] = ???

	def wrapPage(doc: Document): Try[FullPage] =
		doc.select("#comicspot .ksc , #comicspot > a > img").validate(_.size === 1, FailRun(s"no image found ${doc.baseUri}")).map { img =>
			FullPage(loc = doc.baseUri, elements = img.map { imgToElement }, next = (selectNext(doc, "a[rel=next]")))
		}
}

object InverlochArchive extends Core with WrapperTools with StrictLogging {
	def archive = ArchivePointer("http://inverloch.seraph-inn.com/volume1.html")
	def id: String = "AX_Inverloch"
	def name: String = "Inverloch"
	def wrapArchive(doc: Document): Try[FullArchive] =
		doc.getElementById("main").validate(_ != null, FailRun("main id not found")).map { main =>
			val vol = main.child(0).text
			val chapters = main.children.slice(1, 6)
			val cdescs = chapters.flatMap { chapter =>
				val cname = chapter.ownText
				val scenes = chapter.getElementsByTag("a")
				scenes.map { scene =>
					val sname = scene.text
					LinkedChapter(sname, PagePointer(scene.attr("abs:href")), Map("Volume" -> s"$vol; $cname"))
				}
			}
			val volumes = doc.select("#nav > ul > li.lisub > a")
			val nVolInd = volumes.indexWhere { _.text === vol } + 1
			val nextVol = if (1 until volumes.size contains nVolInd) Some(volumes(nVolInd)) else None
			FullArchive(cdescs, nextVol.map { _.attr("abs:href") }.map { ArchivePointer(_) })
		}

	def wrapPage(doc: Document): Try[FullPage] =
		doc.select("#main").validate(_.size === 1, FailRun(s"no image found ${doc.baseUri}")).map { main =>
			val ed = main.select("> p > img").map { imgToElement }
			val next = selectNext(main(0), "a:containsOwn(Next)")
			FullPage(loc = doc.baseUri, elements = ed, next = (next))
		}
}

object TwokindsArchive extends Core with WrapperTools with StrictLogging {
	def archive = ArchivePointer("http://twokinds.keenspot.com/?pageid=3")
	def id: String = "AX_Twokinds"
	def name: String = "Twokinds"

	def wrapArchive(doc: Document): Try[FullArchive] = {

		def extractChapterDescription(element: Element) = for {
			name <- element.select("h4").validate(_.size === 1).map { _.text }
			pageAnchors <- element.select("a").validate(_.size > 0)
			pageUris = pageAnchors.map { _.attr("abs:href") }
		} yield LinkedChapter(name, urisToPagePointer(pageUris))

		Try { doc.select(".chapter").map { extractChapterDescription }.map { _.get } }
			.map { FullArchive(_) }
	}

	def wrapPage(doc: Document): Try[FullPage] =
		selectUnique(doc, "#cg_img img").recoverWith { case FailedStatus(_) => selectUnique(doc, ".comic .alt-container img") }.map { img =>
			val ed = imgToElement(img)
			val next = selectNext(doc, "a#cg_next").recoverWith { case NormalStatus(_) => selectNext(doc, "a#cg_last") }
			FullPage(loc = doc.baseUri, elements = Seq(ed), next = (next))
		}
}

// object CarciphonaWrapper extends Core with StrictLogging {
// 	def id = "X_Carciphona"
// 	def name = "Carciphona"

// 	val first = Uri("http://carciphona.com/view.php?page=cover&chapter=1&lang=")

// 	val extractImageUri = """[\w-]+:url\((.*)\)""".r

// 	def wrapPage(doc: Document): WrappedPage = new WrappedPage {
// 		def document = doc
// 		val next = document.select("#link #nextarea").validate { found(1, "next") }.map { _.attr("abs:href").pipe { absuri } }
// 		val elements = document.select(".page:has(#link)").validate { found(1, "image") }
// 			.map {
// 				_.attr("style")
// 					.pipe { case extractImageUri(img) => img }
// 					.pipe { Uri.parseAndResolve(_, doc.baseUri) }
// 					.pipe { uri => Element(source = uri, origin = doc.baseUri) }
// 			}.pipe { Seq(_) }
// 	}

// }

// object FlipsideWrapper extends Core with StrictLogging {
// 	def id = "X_Flipside"
// 	def name = "Flipside"

// 	val first = Uri("http://flipside.keenspot.com/comic.php?i=1")

// 	def wrapPage(doc: Document): WrappedPage = new WrappedPage {
// 		def document = doc
// 		val next = document.select("[rel=next][accesskey=n]").validate { found(1, "next") }.map { _.attr("abs:href").pipe { absuri } }
// 		val elements = document.select("img.ksc").validate { found(1, "image") }.map { itag =>
// 			Element(source = itag.attr("abs:src"), origin = doc.baseUri, alt = Option(itag.attr("alt")))
// 		}.pipe { Seq(_) }
// 	}

// }

// object DrMcNinjaWrapper extends ChapteredCore with StrictLogging {
// 	def id = "XC_DrMcNinja"
// 	def name = "Dr. McNinja"

// 	val first = Uri("http://drmcninja.com/issues/")

// 	def wrapChapter(doc: Document): WrappedChapter = new WrappedChapter {
// 		def document = doc

// 		def chapter: Seq[Try[Chapter]] = doc.select("#column .serieslist-content > h2 > a").map { href =>
// 			Try(Chapter(
// 				name = href.text,
// 				first = href.attr("abs:href").pipe { absuri }))
// 		}
// 	}

// 	val extractChapter = """http://drmcninja.com/archives/comic/(\d+)p\d+/""".r

// 	def wrapPage(doc: Document): WrappedPage = new WrappedPage {
// 		def document = doc
// 		val next = document.select("#comic-head .next").validate { found(1, "next") }.map { _.attr("abs:href") }
// 			//.recoverWith { case e => Try { doc.baseUri.pipe { case extractChapter(c) => s"http://drmcninja.com/archives/comic/${c.toInt + 1}p1/" } } }
// 			.map { absuri }
// 		val elements = document.select("#comic img").validate { found(1, "image") }.map { itag =>
// 			Element(source = Uri.parseAbsolute(itag.attr("abs:src")), origin = doc.baseUri, alt = Option(itag.attr("alt")), title = Option(itag.attr("title")))
// 		}.pipe { Seq(_) }
// 	}

// }

// }
