package visceljs.render

import viscel.shared.{ImageRef, ChapterPos, Contents, Gallery}
import visceljs.Definitions.{class_chapters, class_dead, class_preview, link_asset, link_index}
import visceljs.{Actions, Body, Data, Make}

import scala.Predef.{$conforms, ArrowAssoc}
import scala.annotation.tailrec
import scalatags.JsDom.Frag
import scalatags.JsDom.all.Tag
import scalatags.JsDom.implicits.stringFrag
import scalatags.JsDom.tags.{SeqFrag, fieldset, header, legend, span}
import scalatags.JsDom.tags2.{article, section}

object Front {


	def gen(data: Data): Body = {

		val Data(narration, Contents(gallery, chapters), bookmark, _) = data


		val top = header(s"${narration.name} ($bookmark/${narration.size})")

		val navigation = Make.navigation(
			link_index("index"),
			Make.fullscreenToggle("TFS"),
			link_asset(data.move(_.first))("first"),
			if (bookmark > 0) Make.postBookmark(narration, 0, data, _ => Actions.gotoFront(narration, scrolltop = false), "remove")
			else span(class_dead, "remove"),
			Make.postForceHint(narration, "force"))

		val preview = {
			val preview1 = data.move(_.next(bookmark - 1).prev(2))
			val preview2 = preview1.next
			val preview3 = preview2.next
			section(class_preview)(
				List(preview1, preview2, preview3).map(p => p -> p.gallery.get)
					.collect { case (p, Some(a)) => link_asset(p)(article(Make.asset(a, data): _*)) })
		}

		def chapterlist: Tag = {
			val assets = gallery.end

			def makeChapField(chap: String, size: Int, gallery: Gallery[ImageRef]): Frag = {
				val (remaining, links) = Range(size, 0, -1).foldLeft((gallery, List[Frag]())) { case ((gal, acc), i) =>
					val next = gal.prev(1)
					(next, link_asset(data.move(_ => next))(s"$i") :: stringFrag(" ") :: acc)
				}

				article(fieldset(legend(chap), links))
			}


			@tailrec
			def build(apos: Int, assets: Gallery[ImageRef], chapters: List[ChapterPos], acc: List[Frag]): List[Frag] = chapters match {
				case ChapterPos(name, cpos) :: ctail =>
					build(cpos, assets.prev(apos - cpos), ctail, makeChapField(name, apos - cpos, assets) :: acc)
				case Nil => acc
			}

			section(class_chapters)(build(assets.pos, assets, chapters, Nil))

		}

		Body(id = "front", title = narration.name,
			frag = List(top, navigation, preview, chapterlist))

	}
}
