package viscel.narration.narrators

import org.scalactic.Accumulation._
import org.scalactic.Good
import viscel.narration.SelectUtil.{elementIntoChapterPointer, elementIntoPointer, extract, imgIntoAsset, placeChapters, queryImage, queryImageInAnchor, queryImageNext, stringToVurl}
import viscel.narration.Templates.{AP, SF}
import viscel.narration.{Narrator, Selection}
import viscel.shared.Story.{Chapter, More}

import scala.Predef.augmentString
import scala.collection.immutable.Set


object Funish {
	def cores: Set[Narrator] = Set(
		AP("NX_Fragile", "Fragile", "http://www.fragilestory.com/archive",
			doc => Selection(doc).unique("#content_inner_pages").many(".c_arch:has(div.a_2)").wrapFlat { chap =>
				val chapter_? = Selection(chap).first("div.a_2 > p").getOne.map(e => Chapter(e.text()))
				val pages_? = Selection(chap).many("a").wrapEach(elementIntoPointer("page"))
				withGood(chapter_?, pages_?)(_ :: _)
			},
			queryImage("#content_comics > a > img")),

		AP("NX_SixGunMage", "6 Gun Mage", "http://www.6gunmage.com/archives.php",
			doc => Selection(doc).many("#bottomleft > select > option[value~=\\d+]").wrapFlat { elem =>
				val tpIndex = elem.text().indexOf("Title Page")
				val page = More(s"http://www.6gunmage.com/index.php?id=${ elem.attr("value") }", "page") :: Nil
				Good(if (tpIndex > 0) Chapter(elem.text().substring(tpIndex + "Title Page ".length)) :: page else page)
			},
			queryImage("#comic")),
		SF("NX_ElGoonishShive", "El Goonish Shive", "http://www.egscomics.com/index.php?id=1", queryImageInAnchor("#comic", "page")),
		SF("NX_TheRockCocks", "The Rock Cocks", "http://www.therockcocks.com/index.php?id=1", queryImageInAnchor("#comic", "page")),
		SF("NX_PragueRace", "Prague Race", "http://www.praguerace.com/index.php?id=1", queryImageInAnchor("#comic", "page")),
		AP("NX_LetsSpeakEnglish", "Let’s Speak English", "http://www.marycagle.com/archive.php",
			doc => Selection(doc).many("#pagecontent > p > a").wrapEach(elementIntoPointer("page")),
			doc => {
				val asset_? = Selection(doc).unique("#comic").wrapOne(imgIntoAsset)
				val comment_? = Selection(doc).many("#newsarea > *").get.map(_.drop(2).dropRight(1).map(_.text()).mkString("\n"))
				withGood(asset_?, comment_?) { (asset, comment) => asset.copy(metadata = asset.metadata.updated("longcomment", comment)) :: Nil }
			}),
		AP("NX_GoGetARoomie", "Go Get a Roomie!", "http://www.gogetaroomie.com/archive.php",
			doc => Selection(doc).unique("#comicwrap").wrapOne { comicwrap =>
				val pages_? = Selection(comicwrap).many("> select > option[value~=^\\d+$]").wrapEach(e =>
					extract(More(s"http://www.gogetaroomie.com/index.php?id=${e.attr("value").toInt}","page")))
				val chapters_? = Selection(comicwrap).many("h2 a").wrapEach(elementIntoChapterPointer("page")).map(_.map(cp => (cp(0), cp(1))))
				withGood(pages_?, chapters_?) { (pages, chapters) =>
					placeChapters(pages, chapters)
				}
			},
			queryImage("#comic")),
		SF("NX_CliqueRefresh","Clique Refresh", "http://cliquerefresh.com/comic/start-it-up/", queryImageInAnchor(".comicImg img", "page")),
		SF("NX_Candi", "Candi", "http://candicomics.com/d/20040625.html", queryImageNext("#comicplace > span > img", "#comicnav a:has(img#next_day2)", "page"))
	)
}
