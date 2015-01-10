package viscel.narration.narrators

import org.jsoup.nodes.Document
import org.scalactic.Accumulation.withGood
import org.scalactic.{Bad, Good, ErrorMessage, Every, Or}
import viscel.narration.SelectUtil.{storyFromOr, selectNext, elementIntoPointer, imgIntoAsset, extractUri}
import viscel.narration.{Selection, Narrator}
import viscel.shared.{Story, AbsUri}
import viscel.shared.Story.{Chapter, More}

import scala.collection.JavaConverters.iterableAsScalaIterableConverter
import scala.Predef.augmentString
import scala.collection.immutable.Set

object Sequential {
	case class AutoNext(id: String, name: String, first: AbsUri, selectImage: Selection => Selection) extends Narrator {

		def archive = More(first, "page") :: Nil

		def wrapPage(doc: Document): Or[List[Story], Every[ErrorMessage]] =
			Selection(doc).unique("#comic_page").wrapEach(imgIntoAsset)

		def wrapChapter(doc: Document): Or[List[Story], Every[ErrorMessage]] = {
			val pages_? = Selection(doc).first("#page_select").many("option:not([selected=selected])").wrapEach { elementIntoPointer("page") }
			val currentPage_? = wrapPage(doc)
			val nextChapter_? = Selection(doc).first(".moderation_bar").optional("a:has(img[title=Next Chapter])").wrap(selectNext("chapter"))
			val chapter_? = Selection(doc).first("select[name=chapter_select]").unique("option[selected=selected]").getOne.map(e => Chapter(e.text) :: Nil)
			withGood(chapter_?, currentPage_?, pages_?, nextChapter_?) { _ ::: _ ::: _ ::: _ }
		}

		def wrap(doc: Document, kind: String): List[Story] = storyFromOr {
			val imageSelection = selectImage(Selection(doc))
			val images_? = imageSelection.wrapEach(imgIntoAsset)
			val next_? = imageSelection.getOne match {
				case Good(img) =>
					img.parents().asScala.find(e =>
						e.tagName() == "a" &&
							e.hasAttr("href") &&
							!e.attr("href").matches(""".*(?i)\\.(jpe?g|gif|png|bmp)(\\W|$).*""")).map(elementIntoPointer("page")) match {
						case None => Selection(doc).all("a[rel=next]").wrap(selectNext("page")) match {
							case some @ Good(x :: xs) => some
							case other => Selection(doc).all("a:containsOwn(next)").wrap(selectNext("page"))
						}
						case Some(res) => res.map(_ :: Nil)
					}
				case Bad(msg) => Bad(msg)
			}
			withGood(images_?, next_?) { _ ::: _ }
		}
	}

	def cores: Set[AutoNext] = Set()

}
