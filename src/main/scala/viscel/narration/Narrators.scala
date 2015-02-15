package viscel.narration

import org.scalactic.Accumulation.{convertGenTraversableOnceToCombinable, withGood}
import org.scalactic.Good
import org.scalactic.TypeCheckedTripleEquals._
import viscel.narration.Data.{Article, Chapter}
import viscel.narration.Queries._
import viscel.narration.Templates.{AP, SF}
import viscel.narration.narrators._
import viscel.scribe.narration.SelectMore._
import viscel.scribe.narration.{More, Narrator, Selection}
import viscel.scribe.report.ReportTools._

import scala.Predef.{$conforms, ArrowAssoc, augmentString}
import scala.collection.immutable.Set


object Narrators {

	private val staticV2 =
		inlineCores ++
			KatBox.cores ++
			Batoto.cores ++
			PetiteSymphony.cores ++
			Snafu.cores ++
			CloneManga.cores ++
			Individual.cores ++
			Set()

	def calculateAll() = staticV2 ++ Metarrators.cores() ++ Vid.load()

	def update() = {
		cached = calculateAll()
		narratorMap = all.map(n => n.id -> n).toMap
	}

	@volatile private var cached: Set[Narrator] = calculateAll()
	def all: Set[Narrator] = synchronized(cached)

	@volatile private var narratorMap = all.map(n => n.id -> n).toMap
	def get(id: String): Option[Narrator] = narratorMap.get(id)


	private lazy val inlineCores = Set(
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
				val page = More(s"http://www.6gunmage.com/index.php?id=${ elem.attr("value") }") :: Nil
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
					extract(More(s"http://www.gogetaroomie.com/index.php?id=${ e.attr("value").toInt }")))
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
				if (elem.tagName() === "b") extract { Chapter(elem.text()) }
				else extractMore(elem)
			},
			queryImage("#comic")),
		SF("NX_Everblue", "Everblue", "http://everblue-comic.com/archive.php",
			Selection(_).unique("#archive-thumbs").many("h3, a").wrapEach { elem =>
				if (elem.tagName() === "h3") extract { Chapter(elem.text()) }
				else Selection(elem).unique("img").wrapOne { img =>
					val origin = elem.attr("abs:href")
					val source = img.attr("abs:src").replace("/admin/img/thumb/", "/img/comic/")
					Good(Article(source, origin))
				}
			}),
		AP("NX_Amya", "Amya", "http://www.amyachronicles.com/archives",
			Selection(_).many("a:has(img[src~=images/chap\\d+)").wrapFlat(elementIntoChapterPointer),
			queryImageInAnchor("#comic img")),
		AP("NX_ToiletGenie", "Toilet Genie", "http://www.storyofthedoor.com/archive/",
			queryMixedArchive("#chapter_table td[colspan=4] a h2, #chapter_table a[onmouseout=hideddrivetip()]"),
			queryImage("#comic_image")),
		SF("NX_AvasDemon", "Ava’s Demon", "http://www.avasdemon.com/chapters.php",
			Selection(_).many("table[id~=chapter\\d+_table]").wrap {
				_.zipWithIndex.map { case (elem, idx) =>
					Selection(elem).many("a").wrap { as =>
						Good(Chapter(s"Chapter ${ idx + 1 }") ::
							as.sortBy(_.text()).map { a =>
								val origin = a.attr("abs:href")
								val source = s"http://www.avasdemon.com/pages/${ a.text() }.png"
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
