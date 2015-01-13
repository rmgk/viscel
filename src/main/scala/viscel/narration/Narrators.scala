package viscel.narration

import org.scalactic.Accumulation.withGood
import org.scalactic.Good
import viscel.narration.SelectUtil.{elementIntoChapterPointer, elementIntoPointer, extract, imgIntoAsset, placeChapters, queryImage, queryImageInAnchor, queryImageNext, stringToVurl}
import viscel.narration.Templates.{AP, SF}
import viscel.narration.narrators._
import viscel.shared.Story.More.{Unused, Page}
import viscel.shared.Story.{Chapter, More}

import scala.Predef.{ArrowAssoc, augmentString}
import scala.collection.immutable.Set


object Narrators {

	private val static = inlineCores ++
		KatBox.cores ++
		PetiteSymphony.cores ++
		WordpressEasel.cores ++
		Batoto.cores ++
		Set(Flipside, Everafter, CitrusSaburoUta, Misfile,
			Twokinds, JayNaylor.BetterDays, JayNaylor.OriginalLife, MenageA3,
			Building12)
	private def dynamic = CloneManga.MetaClone.load() ++ MangaHere.MetaCore.load() ++ Fakku.Meta.load()

	val all: Set[Narrator] = static ++ dynamic
	private val narratorMap = all.map(n => n.id -> n).toMap

	def get(id: String): Option[Narrator] = narratorMap.get(id)


	private def inlineCores = Set(
		AP("NX_Fragile", "Fragile", "http://www.fragilestory.com/archive",
			doc => Selection(doc).unique("#content_inner_pages").many(".c_arch:has(div.a_2)").wrapFlat { chap =>
				val chapter_? = Selection(chap).first("div.a_2 > p").getOne.map(e => Chapter(e.text()))
				val pages_? = Selection(chap).many("a").wrapEach(elementIntoPointer(Page))
				withGood(chapter_?, pages_?)(_ :: _)
			},
			queryImage("#content_comics > a > img")),

		AP("NX_SixGunMage", "6 Gun Mage", "http://www.6gunmage.com/archives.php",
			doc => Selection(doc).many("#bottomleft > select > option[value~=\\d+]").wrapFlat { elem =>
				val tpIndex = elem.text().indexOf("Title Page")
				val page = More(s"http://www.6gunmage.com/index.php?id=${ elem.attr("value") }", Page) :: Nil
				Good(if (tpIndex > 0) Chapter(elem.text().substring(tpIndex + "Title Page ".length)) :: page else page)
			},
			queryImage("#comic")),
		SF("NX_ElGoonishShive", "El Goonish Shive", "http://www.egscomics.com/index.php?id=1", queryImageInAnchor("#comic", Page)),
		SF("NX_TheRockCocks", "The Rock Cocks", "http://www.therockcocks.com/index.php?id=1", queryImageInAnchor("#comic", Page)),
		SF("NX_PragueRace", "Prague Race", "http://www.praguerace.com/index.php?id=1", queryImageInAnchor("#comic", Page)),
		AP("NX_LetsSpeakEnglish", "Let’s Speak English", "http://www.marycagle.com/archive.php",
			doc => Selection(doc).many("#pagecontent > p > a").wrapEach(elementIntoPointer(Page)),
			doc => {
				val asset_? = Selection(doc).unique("#comic").wrapOne(imgIntoAsset)
				val comment_? = Selection(doc).many("#newsarea > *").get.map(_.drop(2).dropRight(1).map(_.text()).mkString("\n"))
				withGood(asset_?, comment_?) { (asset, comment) => asset.copy(metadata = asset.metadata.updated("longcomment", comment)) :: Nil }
			}),
		AP("NX_GoGetARoomie", "Go Get a Roomie!", "http://www.gogetaroomie.com/archive.php",
			doc => Selection(doc).unique("#comicwrap").wrapOne { comicwrap =>
				val pages_? = Selection(comicwrap).many("> select > option[value~=^\\d+$]").wrapEach(e =>
					extract(More(s"http://www.gogetaroomie.com/index.php?id=${ e.attr("value").toInt }", Page)))
				val chapters_? = Selection(comicwrap).many("h2 a").wrapEach(elementIntoChapterPointer(Page)).map(_.map(cp => (cp(0), cp(1))))
				withGood(pages_?, chapters_?) { (pages, chapters) =>
					placeChapters(pages, chapters)
				}
			},
			queryImage("#comic")),
		SF("NX_CliqueRefresh", "Clique Refresh", "http://cliquerefresh.com/comic/start-it-up/", queryImageInAnchor(".comicImg img", Page)),
		SF("NX_Candi", "Candi", "http://candicomics.com/d/20040625.html", queryImageNext("#comicplace > span > img", "#comicnav a:has(img#next_day2)", Page)),
		SF("NX_Goblins", "Goblins", "http://www.goblinscomic.org/06252005/", queryImageNext("#comic > table > tbody > tr > td > img", "#navigation > div.nav-next > a", Unused)),
		AP("NX_Skullkickers", "Skull☠kickers", "http://comic.skullkickers.com/archive.php",
			Selection(_).many("#sleft > h2 > a").wrapFlat(elementIntoChapterPointer(Page)),
			queryImageInAnchor("#sleft img.ksc", Page))
	)

}
