package viscel.narration.narrators

import java.net.URL

import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import org.scalactic.{Every, Good, Or}
import viscel.narration.Data.{Chapter, listToMap, mapToList}
import viscel.narration.Queries._
import viscel.narration.Templates
import viscel.scribe.narration.SelectMore._
import viscel.scribe.narration.{More, Narrator, Normal, Selection, Story, Volatile}
import viscel.scribe.report.Report
import viscel.scribe.report.ReportTools._

import scala.Predef.augmentString
import scala.collection.immutable.Set

object Individual {

	object Building12 extends Narrator {
		def archive = More("http://www.building12.net/archives.htm", Volatile) :: Nil

		def id: String = "NX_Building12"

		def name: String = "Building 12"

		def wrapIssue(doc: Document): Or[List[Story], Every[Report]] = {
			val elements_? = Selection(doc).many("a[href~=issue\\d+/.*\\.htm$]:has(img)").wrapEach { anchor =>
				val element_? = Selection(anchor).unique("img").wrapOne { imgIntoAsset }
				val origin_? = extractURL(anchor)
				withGood(element_?, origin_?) { (element, origin) =>
					element.copy(
						blob = element.blob.map(_.toString.replace("sm.", ".")),
						origin = Some(origin),
						data = mapToList(listToMap(element.data) - "width" - "height"))
				}
			}
			cons(Good(Chapter("issue\\d+".r.findFirstIn(doc.baseUri()).getOrElse("Unknown Issue"))), elements_?)
		}

		def wrap(doc: Document, more: More): List[Story] Or Every[Report] = more match {
			case More(_, Volatile, _) => Selection(doc).many("a[href~=issue\\d+\\.htm$]").wrapEach(extractMore)
			case _ => wrapIssue(doc)
		}
	}


	object Candi extends Narrator {
		def archive = More("http://candicomics.com/archive.html", Volatile, "archive" :: Nil) :: Nil

		def id: String = "NX_Candi"

		def name: String = "Candi"

		def wrapArchive(doc: Document): Or[List[Story], Every[Report]] = {
			val volumes_? = morePolicy(Volatile,
				Selection(doc).many("#candimidd > p:nth-child(2) a").wrapEach { extractMore })
			// the list of volumes is also the first volume, wrap this directly
			val firstVolume_? = wrapVolume(doc)

			withGood(firstVolume_?, volumes_?) { (first, volumes) =>
				first ::: volumes.drop(1)
			}
		}

		def wrapVolume(doc: Document): Or[List[Story], Every[Report]] =
			Selection(doc).many("#candimidd > table > tbody > tr > td:nth-child(2n) a").wrapFlat { elementIntoChapterPointer }


		def wrap(doc: Document, more: More): List[Story] Or Every[Report] = more match {
			case More(_, Volatile, "archive" :: Nil) => wrapArchive(doc)
			case More(_, Volatile, Nil) => wrapVolume(doc)
			case _ => queryImageNext("#comicplace > span > img", "#comicnav a:has(img#next_day2)")(doc)
		}
	}


	val Flipside = Templates.AP("NX_Flipside", "Flipside", "http://flipside.keenspot.com/chapters.php",
		Selection(_).many("td:matches(Chapter|Intermission)").wrapFlat { data =>
			val pages_? = Selection(data).many("a").wrapEach(extractMore).map { _.distinct }
			val name_? = if (data.text.contains("Chapter"))
				Selection(data).unique("td:root > div:first-child").getOne.map { _.text() }
			else
				Selection(data).unique("p > b").getOne.map { _.text }

			withGood(pages_?, name_?) { (pages, name) =>
				Chapter(name) :: pages
			}
		},
		Selection(_).unique("img.ksc").wrapEach(imgIntoAsset))


	object Inverloch extends Narrator {
		override def id: String = "NX_Inverloch"
		override def name: String = "Inverloch"
		override def archive: List[Story] = Range.inclusive(1, 5).map(i => More(s"http://inverloch.seraph-inn.com/volume$i.html", Normal, "archive" :: Nil)).toList
		override def wrap(doc: Document, more: More): List[Story] Or Every[Report] = more match {
			case More(_, _, "archive" :: Nil) => Selection(doc).many("#main p:containsOwn(Chapter)").wrapFlat { chap =>
				cons(
					extract(Chapter(chap.ownText())),
					Selection(chap).many("a").wrapEach(extractMore))
			}
			case _ =>
				if (doc.baseUri() == "http://inverloch.seraph-inn.com/viewcomic.php?page=765") Good(Nil)
				else queryImageNext("#main > p:nth-child(1) > img", "#main a:containsOwn(Next)")(doc)
		}
	}

	object JayNaylor {
		def common(id: String, name: String, archiveUri: URL) = Templates.AP(id, name, archiveUri,
			doc => Selection(doc).many("#chapters li > a").wrapFlat { elementIntoChapterPointer },
			queryImages("#comicentry .content img"))

		def BetterDays = common("NX_BetterDays", "Better Days", "http://jaynaylor.com/betterdays/archives/chapter-1-honest-girls/")

		def OriginalLife = common("NX_OriginalLife", "Original Life", "http://jaynaylor.com/originallife/")

	}

	object KeyShanShan {
		def Common(cid: String, cname: String, url: String) = Templates.SF(cid, cname, url, doc => {
			val imagenext_? = queryImageInAnchor("img[src~=chapter/\\d+/\\d+")(doc)
			doc.baseUri() match {
				case rex"pageid=001&chapterid=($cid\d+)" => cons(Good(Chapter(s"Chapter $cid")), imagenext_?)
				case _ => imagenext_?
			}
		})

		val Key = Common("NX_Key", "Key", "http://key.shadilyn.com/view.php?pageid=001&chapterid=1")
		val ShanShan = Common("NX_ShanShan", "Shan Shan", "http://shanshan.upperrealms.com/view.php?pageid=001&chapterid=1")

	}


	object MenageA3 extends Narrator {
		def archive = More("http://www.ma3comic.com/archive/volume1", Volatile, "archive" :: Nil) :: Nil

		def id: String = "NX_MenageA3"

		def name: String = "Ménage à 3"

		def wrapArchive(doc: Document): Or[List[Story], Every[Report]] = {
			val volumes_? = morePolicy(Volatile,
				Selection(doc).many("#archive_browse a[href~=.*archive/volume\\d+$]").wrapEach { extractMore })
			// the list of volumes is also the first volume, wrap this directly
			val firstVolume_? = wrapVolume(doc)

			withGood(firstVolume_?, volumes_?) { (first, volumes) =>
				Chapter(s"Volume 1") :: first ::: volumes.drop(1).zipWithIndex.flatMap { case (v, i) => Chapter(s"Volume ${ i + 2 }") :: v :: Nil }
			}
		}

		def wrapVolume(doc: Document): Or[List[Story], Every[Report]] =
			Selection(doc)
				.unique("#archive_chapters")
				.many("a[href~=/strips-ma3/]").wrapEach { extractMore }


		def wrap(doc: Document, more: More): List[Story] Or Every[Report] = more match {
			case More(_, Volatile, "archive" :: Nil) => wrapArchive(doc)
			case More(_, Volatile, Nil) => wrapVolume(doc)
			case _ => queryImage("#cc img")(doc)
		}
	}


	object Misfile extends Narrator {
		def archive = More("http://www.misfile.com/archives.php?arc=1&displaymode=wide&", Volatile) :: Nil

		def id: String = "NX_Misfile"

		def name: String = "Misfile"

		def wrapArchive(doc: Document): Or[List[Story], Every[Report]] = {
			val chapters_? = Selection(doc).many("#comicbody a:matchesOwn(^Book #\\d+$)").wrapFlat { anchor =>
				withGood(extractMore(anchor)) { pointer =>
					Chapter(anchor.ownText()) :: pointer :: Nil
				}
			}
			// the list of chapters is also the first page, wrap this directly
			val firstPage_? = wrapPage(doc)

			withGood(firstPage_?, chapters_?) { (page, chapters) =>
				Chapter("Book #1") :: page ::: chapters
			}
		}

		def wrapPage(doc: Document): Or[List[Story], Every[Report]] = {
			val elements_? = Selection(doc)
				.unique(".comiclist table.wide_gallery")
				.many("[id~=^comic_\\d+$] .picture a").wrapEach { anchor =>
				val element_? = Selection(anchor).unique("img").wrapOne { imgIntoAsset }
				val origin_? = extractURL(anchor)
				withGood(element_?, origin_?) { (element, origin) =>
					element.copy(
						blob = element.blob.map(_.toString.replace("/t", "/")),
						origin = Some(origin),
						data = mapToList(listToMap(element.data) - "width" - "height"))
				}
			}
			val next_? = Selection(doc).all("a.next").wrap { selectMore }

			append(elements_?, next_?)
		}

		def wrap(doc: Document, more: More): Or[List[Story], Every[Report]] = more match {
			case More(_, Volatile, _) => wrapArchive(doc)
			case _ => wrapPage(doc)
		}
	}


	object NamirDeiter extends Narrator {
		override def id: String = "NX_NamirDeiter"
		override def name: String = "Namir Deiter"
		override def archive: List[Story] = More(s"http://www.namirdeiter.com/archive/index.php?year=1", Volatile, "archive" :: Nil) :: Nil

		def wrapIssue(doc: Document): Or[List[Story], Every[Report]] = Selection(doc).many("table #arctitle > a").wrapFlat(elementIntoChapterPointer)

		override def wrap(doc: Document, more: More): Or[List[Story], Every[Report]] = more match {
			case More(_, Volatile, "archive" :: Nil) => append(
				wrapIssue(doc),
				morePolicy(Volatile, Selection(doc).many("body > center > div > center > h2 > a").wrapEach(extractMore)))
			case More(_, Volatile, Nil) => wrapIssue(doc)
			case _ =>
				if (doc.baseUri() == "http://www.namirdeiter.com/comics/index.php?date=20020819") Good(Nil)
				else if (doc.baseUri() == "http://www.namirdeiter.com/") queryImage("#comic > img")(doc)
				else queryImageInAnchor("body > center > div > center:nth-child(3) > table center img")(doc)
		}
	}


	object Twokinds extends Narrator {

		def archive = More("http://twokinds.keenspot.com/?p=archive", Volatile, "archive" :: Nil) :: More("http://twokinds.keenspot.com/index.php", Volatile, "main" :: Nil) :: Nil

		def id: String = "NX_Twokinds"

		def name: String = "Twokinds"

		def wrapArchive(doc: Document): List[Story] Or Every[Report] = {
			Selection(doc).many(".archive .chapter").wrapFlat { chapter =>
				val title_? = Selection(chapter).unique("h4").getOne.map(_.ownText())
				val links_? = Selection(chapter).many("a").wrapEach { extractMore }
				withGood(title_?, links_?) { (title, links) =>
					Chapter(title) :: links
				}
			}
		}

		def wrap(doc: Document, more: More): Or[List[Story], Every[Report]] = more match {
			case More(_, Volatile, "archive" :: Nil) => wrapArchive(doc)
			case More(_, Volatile, "main" :: Nil) =>  Selection(doc).unique(".comic img[src~=images/\\d+\\.\\w+]").wrapEach { imgIntoAsset }
			case _ => Selection(doc).unique("#cg_img img").wrapEach { imgIntoAsset }
		}
	}



	object YouSayItFirst extends Narrator {
		override def id: String = "NX_YouSayItFirst"
		override def name: String = "You Say It First"
		override def archive: List[Story] = Range.inclusive(1, 9).map(i => More(s"http://www.yousayitfirst.com/archive/index.php?year=$i", Volatile)).toList
		override def wrap(doc: Document, more: More): Or[List[Story], Every[Report]] = more match {
			case More(_, Volatile, _) => Selection(doc).many("table #number a").wrapFlat(elementIntoChapterPointer)
			case _ =>
				if (doc.baseUri() == "http://www.soapylemon.com/") Good(Nil)
				else queryImageInAnchor("body > center > div.mainwindow > center:nth-child(2) > table center img")(doc)
		}
	}



	object UnlikeMinerva extends Narrator {
		override def id: String = "NX_UnlikeMinerva"
		override def name: String = "Unlike Minerva"
		override def archive: List[Story] = Range.inclusive(1, 25).map(i => More(s"http://www.unlikeminerva.com/archive/phase1.php?week=$i")).toList :::
			Range.inclusive(26, 130).map(i => More(s"http://www.unlikeminerva.com/archive/index.php?week=$i")).toList
		override def wrap(doc: Document, more: More): Or[List[Story], Every[Report]] =
			Selection(doc).many("center > img[src~=http://www.unlikeminerva.com/archive/]").wrapEach { img =>
				withGood(imgIntoAsset(img), extract(img.parent().nextElementSibling().text())) { (a, txt) =>
					a.copy(data = a.data ::: "longcomment" :: txt :: Nil)
				}
			}
	}



	val cores = Set(Building12, Candi, Flipside, Inverloch, JayNaylor.BetterDays, JayNaylor.OriginalLife,
		KeyShanShan.Key, KeyShanShan.ShanShan, MenageA3, Misfile, NamirDeiter, Twokinds, YouSayItFirst,
		UnlikeMinerva)

}
