package viscel.narration.narrators

import org.jsoup.nodes.{Element, Document}
import org.scalactic.Accumulation._
import org.scalactic.{Good, Or, Every, ErrorMessage}
import viscel.Log
import viscel.narration.SelectUtil.{elementIntoPointer, storyFromOr, queryImage, queryImageInAnchor, imgIntoAsset}
import viscel.narration.{Selection, Narrator}
import viscel.shared.{AbsUri, Story}
import viscel.shared.Story.{Chapter, More}

import scala.collection.immutable.Set


object Funish {
	case class AP(override val id: String,
								override val name: String,
								start: AbsUri,
								wrapArchive: Document => List[Story] Or Every[ErrorMessage],
								wrapPage: Document => List[Story] Or Every[ErrorMessage]) extends Narrator {
		override def archive: List[Story] = More(start, "archive") :: Nil
		override def wrap(doc: Document, kind: String): List[Story] = storyFromOr(kind match {
			case "archive" => wrapArchive(doc)
			case "page" => wrapPage(doc)
		})
	}
	case class SF(override val id: String,
								override val name: String,
								start: AbsUri,
								wrapPage: Document => List[Story] Or Every[ErrorMessage]) extends Narrator {
		override def archive: List[Story] = More(start, "page") :: Nil
		override def wrap(doc: Document, kind: String): List[Story] = storyFromOr(kind match {
			case "page" => wrapPage(doc)
		})
	}


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
			})
	)
}
