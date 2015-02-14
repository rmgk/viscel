package viscel.narration

import org.scalactic.Accumulation.{convertGenTraversableOnceToCombinable, withGood}
import org.scalactic.Good
import org.scalactic.TypeCheckedTripleEquals._
import viscel.compat.v1.SelectionV1
import viscel.compat.v1.Story.More.{Page, Unused}
import viscel.compat.v1.Story.{Asset, Chapter, More}
import viscel.compat.v1.SelectUtilV1._
import viscel.compat.v1.TemplatesV1.{AP, SF}
import viscel.narration.narrators._
import viscel.scribe.narration.Narrator

import scala.Predef.{$conforms, ArrowAssoc, augmentString}
import scala.collection.immutable.Set


object Narrators {

	private val static = inlineCores ++
		KatBox.cores ++
		PetiteSymphony.cores ++
		WordpressEasel.cores ++
		Batoto.cores ++
		Snafu.cores ++
		CloneManga.cores ++
		Set(Flipside, CitrusSaburoUta, Misfile,
			Twokinds, JayNaylor.BetterDays, JayNaylor.OriginalLife, MenageA3,
			Building12, Candi, YouSayItFirst, Inverloch, UnlikeMinerva, NamirDeiter,
			KeyShanShan.Key, KeyShanShan.ShanShan)

	def calculateAll() = static.map(new NarratorV1Adapter(_): Narrator) ++ Metarrators.cores() ++ Vid.load()

	def update() = {
		cached = calculateAll()
		narratorMap = all.map(n => n.id -> n).toMap
	}

	@volatile private var cached: Set[Narrator] = calculateAll()
	def all: Set[Narrator] = synchronized(cached)

	@volatile private var narratorMap = all.map(n => n.id -> n).toMap
	def get(id: String): Option[Narrator] = narratorMap.get(id)


	private def inlineCores = Set(
		AP("NX_Fragile", "Fragile", "http://www.fragilestory.com/archive",
			doc => SelectionV1(doc).unique("#content_inner_pages").many(".c_arch:has(div.a_2)").wrapFlat { chap =>
				val chapter_? = SelectionV1(chap).first("div.a_2 > p").getOne.map(e => Chapter(e.text()))
				val pages_? = SelectionV1(chap).many("a").wrapEach(elementIntoPointer(Page))
				withGood(chapter_?, pages_?)(_ :: _)
			},
			queryImage("#content_comics > a > img")),

		AP("NX_SixGunMage", "6 Gun Mage", "http://www.6gunmage.com/archives.php",
			doc => SelectionV1(doc).many("#bottomleft > select > option[value~=\\d+]").wrapFlat { elem =>
				val tpIndex = elem.text().indexOf("Title Page")
				val page = More(s"http://www.6gunmage.com/index.php?id=${ elem.attr("value") }", Page) :: Nil
				Good(if (tpIndex > 0) Chapter(elem.text().substring(tpIndex + "Title Page ".length)) :: page else page)
			},
			queryImage("#comic")),
		AP("NX_ElGoonishShive", "El Goonish Shive", "http://www.egscomics.com/archives.php",
			SelectionV1(_).many("#leftarea > h3 > a").wrapFlat(elementIntoChapterPointer(Page)),
			queryImageInAnchor("#comic", Page)),
		SF("NX_TheRockCocks", "The Rock Cocks", "http://www.therockcocks.com/index.php?id=1", queryImageInAnchor("#comic", Page)),
		AP("NX_PragueRace", "Prague Race", "http://www.praguerace.com/archive.php",
			queryChapterArchive("#bottommid > div > div > h2 > a", Page),
			queryImageInAnchor("#comic", Page)),
		AP("NX_LetsSpeakEnglish", "Let’s Speak English", "http://www.marycagle.com/archive.php",
			doc => SelectionV1(doc).many("#pagecontent > p > a").wrapEach(elementIntoPointer(Page)),
			doc => {
				val asset_? = SelectionV1(doc).unique("#comic").wrapOne(imgIntoAsset)
				val comment_? = SelectionV1(doc).many("#newsarea > *").get.map(_.drop(2).dropRight(1).map(_.text()).mkString("\n"))
				withGood(asset_?, comment_?) { (asset, comment) => asset.updateMeta(_.updated("longcomment", comment)) :: Nil }
			}),
		AP("NX_GoGetARoomie", "Go Get a Roomie!", "http://www.gogetaroomie.com/archive.php",
			doc => SelectionV1(doc).unique("#comicwrap").wrapOne { comicwrap =>
				val pages_? = SelectionV1(comicwrap).many("> select > option[value~=^\\d+$]").wrapEach(e =>
					extract(More(s"http://www.gogetaroomie.com/index.php?id=${ e.attr("value").toInt }", Page)))
				val chapters_? = SelectionV1(comicwrap).many("h2 a").wrapEach(elementIntoChapterPointer(Page)).map(_.map(cp => (cp(0), cp(1))))
				withGood(pages_?, chapters_?) { (pages, chapters) =>
					placeChapters(pages, chapters)
				}
			},
			queryImage("#comic")),
		SF("NX_CliqueRefresh", "Clique Refresh", "http://cliquerefresh.com/comic/start-it-up/", queryImageInAnchor(".comicImg img", Page)),
		AP("NX_Goblins", "Goblins", "http://www.goblinscomic.org/archive/",
			SelectionV1(_).many("#column div.entry a").wrapFlat(elementIntoChapterPointer(Page)),
			queryImageNext("#comic > table > tbody > tr > td > img", "#navigation > div.nav-next > a", Page)),
		AP("NX_Skullkickers", "Skull☠kickers", "http://comic.skullkickers.com/archive.php",
			SelectionV1(_).many("#sleft > h2 > a").wrapFlat(elementIntoChapterPointer(Page)),
			queryImageInAnchor("#sleft img.ksc", Page)),
		SF("NX_CoolCatStudio", "Cool Cat Studio", "http://coolcatstudio.com/strips-cat/first", queryImageInAnchor("#comic img", Page)),
		SF("NX_StickyDillyBuns", "Sticky Dilly Buns", "http://www.stickydillybuns.com/strips-sdb/awesome_leading_man",
			doc => queryImageInAnchor("#comic img", Page)(doc).orElse(queryNext("#cndnext", Page)(doc))),
		SF("NX_EerieCuties", "Eerie Cuties", "http://www.eeriecuties.com/strips-ec/%28chapter_1%29_it_is_gonna_eat_me%21", queryImageInAnchor("#comic img[src~=/comics/]", Page)),
		SF("NX_MagicChicks", "Magic Chicks", "http://www.magickchicks.com/strips-mc/tis_but_a_trifle", queryImageInAnchor("#comic img[src~=/comics/]", Page)),
		AP("NX_PennyAndAggie", "Penny & Aggie", "http://www.pennyandaggie.com/index.php?p=1",
			SelectionV1(_).many("form[name=jump] > select[name=menu] > option[value]").wrapFlat(elementIntoChapterPointer(Page)),
			queryImageNext(".comicImage", "center > span.a11pixbluelinks > div.mainNav > a:has(img[src~=next_day.gif])", Page)),
		SF("NX_SandraOnTheRocks", "Sandra on the Rocks", "http://www.sandraontherocks.com/strips-sotr/start_by_running", queryImageInAnchor("#comic img[src~=/comics/]", Page)),
		AP("NX_MegaTokyo", "MegaTokyo", "http://megatokyo.com/archive.php",
			SelectionV1(_).many("div.content:has(a[id~=^C-\\d+$]").wrapFlat { chap =>
				val chapter_? = extract(Chapter(chap.child(0).text()))
				val elements_? = SelectionV1(chap).many("li a").wrapEach(elementIntoPointer(Page))
				cons(chapter_?, elements_?)
			},
			queryImageNext("#strip img", "#comic .next a", Page)),
		SF("NX_WhatBirdsKnow", "What Birds Know", "http://fribergthorelli.com/wbk/index.php/page-1/", queryImageNext("#comic > img", ".nav-next > a", Page)),
		AP("NX_TodayNothingHappened", "Today Nothing Happened", "http://www.todaynothinghappened.com/archive.php",
			SelectionV1(_).many("#wrapper > div.rant a.link").wrapEach(elementIntoPointer(Page)),
			queryImage("#comic > img")),
		AP("NX_RedString", "Red String", "http://www.redstring.strawberrycomics.com/archive.php",
			SelectionV1(_).many("#comicwrap h2 > a").wrapFlat(elementIntoChapterPointer(Page)),
			queryImageInAnchor("#comic", Page)),
		SF("NX_Dreamless", "Dreamless", "http://dreamless.keenspot.com/d/20090105.html",
			doc => queryImageNext("img.ksc", "a:has(#next_day1)", Page)(doc).orElse(queryNext("a:has(#next_day1)", Page)(doc))),
		AP("NX_PhoenixRequiem", "The Phoenix Requiem", "http://requiem.seraph-inn.com/archives.html",
			SelectionV1(_).many("#container div.main > table tr:contains(Chapter)").wrapFlat { chap =>
				val chapter_? = extract(Chapter(chap.child(0).text()))
				val elements_? = SelectionV1(chap).many("a").wrapEach(elementIntoPointer(Page))
				cons(chapter_?, elements_?)
			},
			queryImage("#container img[src~=^pages/]")),
		SF("NX_ErrantStory", "Errant Story", "http://www.errantstory.com/2002-11-04/15", queryImageNext("#comic > img", "#column > div.nav > h4.nav-next > a", Page)),
		AP("NX_StandStillStaySilent", "Stand Still Stay Silent", "http://www.sssscomic.com/?id=archive",
			queryMixedArchive("#main_text div.archivediv h2, #main_text div.archivediv a", Page),
			queryImage("#wrapper2 img")),
		SF("NX_Awaken", "Awaken", "http://awakencomic.com/index.php?id=1", queryImageInAnchor("#comic", Page)),
		SF("NX_DangerouslyChloe", "Dangerously Chloe", "http://www.dangerouslychloe.com/strips-dc/chapter_1_-_that_damned_girl", queryImageInAnchor("#comic img", Unused)),
		SF("NX_YouSuck", "You Suck", "http://yousuckthecomic.com/go/1",
			doc => queryImageNext("img[src=/comics/yousuck_0096-1.png]", "a[href=http://yousuckthecomic.com/go/97]", Unused)(doc)
				.orElse(queryImageInAnchor("img.comicpage", Unused)(doc))),
		SF("NX_Nimona", "Nimona", "http://gingerhaze.com/nimona/comic/page-1",
			queryImageNext("img[src~=/nimona-pages/]", "a:has(img[src=http://gingerhaze.com/sites/default/files/comicdrop/comicdrop_next_label_file.png])", Unused)),
		AP("NX_Monsterkind", "Monsterkind", "http://monsterkind.enenkay.com/comic/archive",
			SelectionV1(_).unique("#content > div.text").wrapFlat { content =>
				val chapters_? = SelectionV1(content).many("h2").wrapEach(h => extract(Chapter(h.text())))
				val pages_? = SelectionV1(content).many("p").wrapEach { p =>
					SelectionV1(p).many("a").wrapEach(elementIntoPointer(Page))
				}
				withGood(chapters_?, pages_?) { (chaps, pages) =>
					chaps.zip(pages).map(t => t._1 :: t._2).flatten
				}
			},
			queryImage("#comic img")),
		SF("NX_Solstoria", "Solstoria", "http://solstoria.net/?webcomic1=54", queryImageInAnchor("#webcomic > div.webcomic-image img", Unused)),
		AP("NX_TheBoyWhoFell", "The Boy Who Fell", "http://www.boywhofell.com/chapter.php",
			SelectionV1(_).many("#comicarea h2 a").wrapFlat(elementIntoChapterPointer(Page)),
			queryImageInAnchor("#comic", Page)),
		SF("NX_DominicDeegan", "Dominic Deegan", "http://www.dominic-deegan.com/view.php?date=2002-05-21",
			doc => append(queryImages("body > div.comic > img")(doc), queryNext("#bottom a:has(img[alt=Next])", Unused)(doc))),
		AP("NX_DreamScar", "dream*scar", "http://dream-scar.net/archive.php",
			SelectionV1(_).many("#static > b , #static > a").wrapEach { elem =>
				if (elem.tagName() === "b") extract { Chapter(elem.text()) }
				else elementIntoPointer(Page)(elem)
			},
			queryImage("#comic")),
		SF("NX_Everblue", "Everblue", "http://everblue-comic.com/archive.php",
			SelectionV1(_).unique("#archive-thumbs").many("h3, a").wrapEach { elem =>
				if (elem.tagName() === "h3") extract { Chapter(elem.text()) }
				else SelectionV1(elem).unique("img").wrapOne { img =>
					val origin = elem.attr("abs:href")
					val source = img.attr("abs:src").replace("/admin/img/thumb/", "/img/comic/")
					Good(Asset(source, origin))
				}
			}),
		AP("NX_Amya", "Amya", "http://www.amyachronicles.com/archives",
			SelectionV1(_).many("a:has(img[src~=images/chap\\d+)").wrapFlat(elementIntoChapterPointer(Page)),
			queryImageInAnchor("#comic img", Page)),
		AP("NX_ToiletGenie", "Toilet Genie", "http://www.storyofthedoor.com/archive/",
			queryMixedArchive("#chapter_table td[colspan=4] a h2, #chapter_table a[onmouseout=hideddrivetip()]", Page),
			queryImage("#comic_image")),
		SF("NX_AvasDemon", "Ava’s Demon", "http://www.avasdemon.com/chapters.php",
			SelectionV1(_).many("table[id~=chapter\\d+_table]").wrap {
				_.zipWithIndex.map { case (elem, idx) =>
					SelectionV1(elem).many("a").wrap { as =>
						Good(Chapter(s"Chapter ${ idx + 1 }") ::
							as.sortBy(_.text()).map { a =>
								val origin = a.attr("abs:href")
								val source = s"http://www.avasdemon.com/pages/${ a.text() }.png"
								Asset(source, origin)
							})
					}
				}.combined.map(_.flatten)
			}),
		AP("NX_Spindrift", "Spindrift", "http://www.spindrift-comic.com/archive",
			queryMixedArchive("#pjax-container > div.content > div:nth-child(1) .archivehead .shead , #pjax-container > div.content > div:nth-child(1) .archive-comic-container a", Page),
			queryImage("#comic-image")),
		AP("NX_Anathema", "Anathema", "http://anathema-comics.smackjeeves.com/archive/",
			queryMixedArchive("#chapter_table td[colspan=4] a h2, #chapter_table a[onmouseout=hideddrivetip()]", Page),
			queryImage("#comic_image"))
	)

}
