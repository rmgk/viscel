package viscel.narration.narrators

import java.net.URL

import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import org.scalactic.TypeCheckedTripleEquals._
import org.scalactic.{Every, Good, Or}
import viscel.narration.Data.{Article, Chapter, listToMap, mapToList}
import viscel.narration.Queries._
import viscel.narration.SelectMore._
import viscel.narration.Templates.{AP, SF}
import viscel.narration.{Narrator, SelectMore, Templates}
import viscel.scribe.narration.{More, Normal, Story, Volatile}
import viscel.selection.ReportTools._
import viscel.selection.{Report, Selection}

import scala.Predef.{$conforms, augmentString}
import scala.collection.immutable.Set

object Individual {

	object Building12 extends Narrator {
		def archive = More("http://www.building12.net/archives.htm", Volatile) :: Nil

		def id: String = "NX_Building12"

		def name: String = "Building 12"

		def wrapIssue(doc: Document): Or[List[Story], Every[Report]] = {
			val elements_? = Selection(doc).many("a[href~=issue\\d+/.*\\.htm$]:has(img)").wrapEach { anchor =>
				val element_? = Selection(anchor).unique("img").wrapOne {imgIntoAsset}
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
				Selection(doc).many("#candimidd > p:nth-child(2) a").wrapEach {extractMore})
			// the list of volumes is also the first volume, wrap this directly
			val firstVolume_? = wrapVolume(doc)

			withGood(firstVolume_?, volumes_?) { (first, volumes) =>
				first ::: volumes.drop(1)
			}
		}

		def wrapVolume(doc: Document): Or[List[Story], Every[Report]] =
			Selection(doc).many("#candimidd > table > tbody > tr > td:nth-child(2n) a").wrapFlat {elementIntoChapterPointer}


		def wrap(doc: Document, more: More): List[Story] Or Every[Report] = more match {
			case More(_, Volatile, "archive" :: Nil) => wrapArchive(doc)
			case More(_, Volatile, Nil) => wrapVolume(doc)
			case _ => queryImageNext("#comicplace > span > img", "#comicnav a:has(img#next_day2)")(doc)
		}
	}


	val Flipside = Templates.AP("NX_Flipside", "Flipside", "http://flipside.keenspot.com/chapters.php",
		Selection(_).many("td:matches(Chapter|Intermission)").wrapFlat { data =>
			val pages_? = Selection(data).many("a").wrapEach(extractMore).map {_.distinct}
			val name_? = if (data.text.contains("Chapter"))
				Selection(data).unique("td:root > div:first-child").getOne.map {_.text()}
			else
				Selection(data).unique("p > b").getOne.map {_.text}

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
			doc => Selection(doc).many("#chapters li > a").wrapFlat {elementIntoChapterPointer},
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
				Selection(doc).many("#archive_browse a[href~=.*archive/volume\\d+$]").wrapEach {extractMore})
			// the list of volumes is also the first volume, wrap this directly
			val firstVolume_? = wrapVolume(doc)

			withGood(firstVolume_?, volumes_?) { (first, volumes) =>
				Chapter(s"Volume 1") :: first ::: volumes.drop(1).zipWithIndex.flatMap { case (v, i) => Chapter(s"Volume ${i + 2}") :: v :: Nil }
			}
		}

		def wrapVolume(doc: Document): Or[List[Story], Every[Report]] =
			Selection(doc)
				.unique("#archive_chapters")
				.many("a[href~=/strips-ma3/]").wrapEach {extractMore}


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
				val element_? = Selection(anchor).unique("img").wrapOne {imgIntoAsset}
				val origin_? = extractURL(anchor)
				withGood(element_?, origin_?) { (element, origin) =>
					element.copy(
						blob = element.blob.map(_.toString.replace("/t", "/")),
						origin = Some(origin),
						data = mapToList(listToMap(element.data) - "width" - "height"))
				}
			}
			val next_? = Selection(doc).all("a.next").wrap {selectMore}

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
				val links_? = Selection(chapter).many("a").wrapEach {extractMore}
				withGood(title_?, links_?) { (title, links) =>
					Chapter(title) :: links
				}
			}
		}

		def wrap(doc: Document, more: More): Or[List[Story], Every[Report]] = more match {
			case More(_, Volatile, "archive" :: Nil) => wrapArchive(doc)
			case More(_, Volatile, "main" :: Nil) => Selection(doc).unique(".comic img[src~=images/\\d+\\.\\w+]").wrapEach {imgIntoAsset}
			case _ => Selection(doc).unique("#cg_img img").wrapEach {imgIntoAsset}
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

	val inlineCores = Set(
		AP("NX_Fragile", "Fragile", "http://www.fragilestory.com/archive",
			doc => Selection(doc).unique("#content_inner_pages").many(".c_arch:has(div.a_2)").wrapFlat { chap =>
				val chapter_? = Selection(chap).first("div.a_2 > p").getOne.map(e => Chapter(e.text()))
				val pages_? = Selection(chap).many("a").wrapEach(extractMore)
				withGood(chapter_?, pages_?)(_ :: _)
			},
			queryImage("#content_comics > a > img")),

		AP("NX_SixGunMage", "6 Gun Mage", "http://www.6gunmage.com/archives.php",
			doc => Selection(doc).many("#bottomleft > select > option[value~=\\d+]").wrapFlat { elem =>
				val tpIndex = elem.text().indexOf("Title Page")
				val page = More(s"http://www.6gunmage.com/index.php?id=${elem.attr("value")}") :: Nil
				Good(if (tpIndex > 0) Chapter(elem.text().substring(tpIndex + "Title Page ".length)) :: page else page)
			},
			queryImage("#comic")),
		AP("NX_ElGoonishShive", "El Goonish Shive", "http://www.egscomics.com/archives.php",
			Selection(_).many("#leftarea > h3 > a").wrapFlat(elementIntoChapterPointer),
			queryImageInAnchor("#comic")),
		SF("NX_TheRockCocks", "The Rock Cocks", "http://www.therockcocks.com/index.php?id=1", queryImageInAnchor("#comic")),
		AP("NX_PragueRace", "Prague Race", "http://www.praguerace.com/archive.php",
			queryChapterArchive("#bottommid > div > div > h2 > a"),
			queryImageInAnchor("#comic")),
		AP("NX_LetsSpeakEnglish", "Let’s Speak English", "http://www.marycagle.com/archive.php",
			doc => Selection(doc).many("#pagecontent > p > a").wrapEach(extractMore),
			doc => {
				val asset_? = Selection(doc).unique("#comic").wrapOne(imgIntoAsset)
				val comment_? = Selection(doc).many("#newsarea > *").get.map(_.drop(2).dropRight(1).map(_.text()).mkString("\n"))
				withGood(asset_?, comment_?) { (asset, comment) => asset.copy(data = asset.data ::: "longcomment" :: comment :: Nil) :: Nil }
			}),
		AP("NX_GoGetARoomie", "Go Get a Roomie!", "http://www.gogetaroomie.com/archive.php",
			doc => Selection(doc).unique("#comicwrap").wrapOne { comicwrap =>
				val pages_? = Selection(comicwrap).many("> select > option[value~=^\\d+$]").wrapEach(e =>
					extract(More(s"http://www.gogetaroomie.com/index.php?id=${e.attr("value").toInt}")))
				val chapters_? = Selection(comicwrap).many("h2 a").wrapEach(elementIntoChapterPointer).map(_.map(cp => (cp(0), cp(1))))
				withGood(pages_?, chapters_?) { (pages, chapters) =>
					placeChapters(pages, chapters)
				}
			},
			queryImage("#comic")),
		SF("NX_CliqueRefresh", "Clique Refresh", "http://cliquerefresh.com/comic/start-it-up/", queryImageInAnchor(".comicImg img")),
		AP("NX_Goblins", "Goblins", "http://www.goblinscomic.org/archive/",
			Selection(_).many("#column div.entry a").wrapFlat(elementIntoChapterPointer),
			queryImageNext("#comic > table > tbody > tr > td > img", "#navigation > div.nav-next > a")),
		AP("NX_Skullkickers", "Skull☠kickers", "http://comic.skullkickers.com/archive.php",
			Selection(_).many("#sleft > h2 > a").wrapFlat(elementIntoChapterPointer),
			queryImageInAnchor("#sleft img.ksc")),
		SF("NX_CoolCatStudio", "Cool Cat Studio", "http://coolcatstudio.com/strips-cat/first", queryImageInAnchor("#comic img")),
		SF("NX_StickyDillyBuns", "Sticky Dilly Buns", "http://www.stickydillybuns.com/strips-sdb/awesome_leading_man",
			doc => queryImageInAnchor("#comic img")(doc).orElse(queryNext("#cndnext")(doc))),
		SF("NX_EerieCuties", "Eerie Cuties", "http://www.eeriecuties.com/strips-ec/%28chapter_1%29_it_is_gonna_eat_me%21", queryImageInAnchor("#comic img[src~=/comics/]")),
		SF("NX_MagicChicks", "Magic Chicks", "http://www.magickchicks.com/strips-mc/tis_but_a_trifle", queryImageInAnchor("#comic img[src~=/comics/]")),
		AP("NX_PennyAndAggie", "Penny & Aggie", "http://www.pennyandaggie.com/index.php?p=1",
			Selection(_).many("form[name=jump] > select[name=menu] > option[value]").wrapFlat(elementIntoChapterPointer),
			queryImageNext(".comicImage", "center > span.a11pixbluelinks > div.mainNav > a:has(img[src~=next_day.gif])")),
		SF("NX_SandraOnTheRocks", "Sandra on the Rocks", "http://www.sandraontherocks.com/strips-sotr/start_by_running", queryImageInAnchor("#comic img[src~=/comics/]")),
		AP("NX_MegaTokyo", "MegaTokyo", "http://megatokyo.com/archive.php",
			Selection(_).many("div.content:has(a[id~=^C-\\d+$]").wrapFlat { chap =>
				val chapter_? = extract(Chapter(chap.child(0).text()))
				val elements_? = Selection(chap).many("li a").wrapEach(extractMore)
				cons(chapter_?, elements_?)
			},
			queryImageNext("#strip img", "#comic .next a")),
		SF("NX_WhatBirdsKnow", "What Birds Know", "http://fribergthorelli.com/wbk/index.php/page-1/", queryImageNext("#comic > img", ".nav-next > a")),
		AP("NX_TodayNothingHappened", "Today Nothing Happened", "http://www.todaynothinghappened.com/archive.php",
			Selection(_).many("#wrapper > div.rant a.link").wrapEach(extractMore),
			queryImage("#comic > img")),
		AP("NX_RedString", "Red String", "http://www.redstring.strawberrycomics.com/archive.php",
			Selection(_).many("#comicwrap h2 > a").wrapFlat(elementIntoChapterPointer),
			queryImageInAnchor("#comic")),
		SF("NX_Dreamless", "Dreamless", "http://dreamless.keenspot.com/d/20090105.html",
			doc => queryImageNext("img.ksc", "a:has(#next_day1)")(doc).orElse(queryNext("a:has(#next_day1)")(doc))),
		AP("NX_PhoenixRequiem", "The Phoenix Requiem", "http://requiem.seraph-inn.com/archives.html",
			Selection(_).many("#container div.main > table tr:contains(Chapter)").wrapFlat { chap =>
				val chapter_? = extract(Chapter(chap.child(0).text()))
				val elements_? = Selection(chap).many("a").wrapEach(extractMore)
				cons(chapter_?, elements_?)
			},
			queryImage("#container img[src~=^pages/]")),
		SF("NX_ErrantStory", "Errant Story", "http://www.errantstory.com/2002-11-04/15", queryImageNext("#comic > img", "#column > div.nav > h4.nav-next > a")),
		AP("NX_StandStillStaySilent", "Stand Still Stay Silent", "http://www.sssscomic.com/?id=archive",
			queryMixedArchive("#main_text div.archivediv h2, #main_text div.archivediv a"),
			queryImage("#wrapper2 img")),
		SF("NX_Awaken", "Awaken", "http://awakencomic.com/index.php?id=1", queryImageInAnchor("#comic")),
		SF("NX_DangerouslyChloe", "Dangerously Chloe", "http://www.dangerouslychloe.com/strips-dc/chapter_1_-_that_damned_girl", queryImageInAnchor("#comic img")),
		SF("NX_YouSuck", "You Suck", "http://yousuckthecomic.com/go/1",
			doc => queryImageNext("img[src=/comics/yousuck_0096-1.png]", "a[href=http://yousuckthecomic.com/go/97]")(doc)
				.orElse(queryImageInAnchor("img.comicpage")(doc))),
		SF("NX_Nimona", "Nimona", "http://gingerhaze.com/nimona/comic/page-1",
			queryImageNext("img[src~=/nimona-pages/]", "a:has(img[src=http://gingerhaze.com/sites/default/files/comicdrop/comicdrop_next_label_file.png])")),
		AP("NX_Monsterkind", "Monsterkind", "http://monsterkind.enenkay.com/comic/archive",
			Selection(_).unique("#content > div.text").wrapFlat { content =>
				val chapters_? = Selection(content).many("h2").wrapEach(h => extract(Chapter(h.text())))
				val pages_? = Selection(content).many("p").wrapEach { p =>
					Selection(p).many("a").wrapEach(extractMore)
				}
				withGood(chapters_?, pages_?) { (chaps, pages) =>
					chaps.zip(pages).map(t => t._1 :: t._2).flatten
				}
			},
			queryImage("#comic img")),
		SF("NX_Solstoria", "Solstoria", "http://solstoria.net/?webcomic1=54", queryImageInAnchor("#webcomic > div.webcomic-image img")),
		AP("NX_TheBoyWhoFell", "The Boy Who Fell", "http://www.boywhofell.com/chapter.php",
			Selection(_).many("#comicarea h2 a").wrapFlat(elementIntoChapterPointer),
			queryImageInAnchor("#comic")),
		SF("NX_DominicDeegan", "Dominic Deegan", "http://www.dominic-deegan.com/view.php?date=2002-05-21",
			doc => append(queryImages("body > div.comic > img")(doc), queryNext("#bottom a:has(img[alt=Next])")(doc))),
		AP("NX_DreamScar", "dream*scar", "http://dream-scar.net/archive.php",
			Selection(_).many("#static > b , #static > a").wrapEach { elem =>
				if (elem.tagName() === "b") extract {Chapter(elem.text())}
				else extractMore(elem)
			},
			queryImage("#comic")),
		SF("NX_Everblue", "Everblue", "http://everblue-comic.com/archive.php",
			Selection(_).unique("#archive-thumbs").many("h3, a").wrapEach { elem =>
				if (elem.tagName() === "h3") extract {Chapter(elem.text())}
				else Selection(elem).unique("img").wrapOne { img =>
					val origin = elem.attr("abs:href")
					val source = img.attr("abs:src").replace("/admin/img/thumb/", "/img/comic/")
					Good(Article(source, origin))
				}
			}),
		AP("NX_ToiletGenie", "Toilet Genie", "http://www.storyofthedoor.com/archive/",
			queryMixedArchive("#chapter_table td[colspan=4] a h2, #chapter_table a[onmouseout=hideddrivetip()]"),
			queryImage("#comic_image")),
		SF("NX_AvasDemon", "Ava’s Demon", "http://www.avasdemon.com/chapters.php",
			Selection(_).many("table[id~=chapter\\d+_table]").wrap {
				_.zipWithIndex.map { case (elem, idx) =>
					Selection(elem).many("a").wrap { as =>
						Good(Chapter(s"Chapter ${idx + 1}") ::
							as.sortBy(_.text()).map { a =>
								val origin = a.attr("abs:href")
								val source = s"http://www.avasdemon.com/pages/${a.text()}.png"
								Article(source, origin)
							})
					}
				}.combined.map(_.flatten)
			}),
		AP("NX_Spindrift", "Spindrift", "http://www.spindrift-comic.com/archive",
			queryMixedArchive("#pjax-container > div.content > div:nth-child(1) .archivehead .shead , #pjax-container > div.content > div:nth-child(1) .archive-comic-container a"),
			queryImage("#comic-image")),
		AP("NX_Anathema", "Anathema", "http://anathema-comics.smackjeeves.com/archive/",
			queryMixedArchive("#chapter_table td[colspan=4] a h2, #chapter_table a[onmouseout=hideddrivetip()]"),
			queryImage("#comic_image"))
	)

}
