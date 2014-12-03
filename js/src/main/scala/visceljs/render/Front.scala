package visceljs.render

import viscel.shared.Story.{Chapter, Narration}
import viscel.shared.{Gallery, Story}
import visceljs.Definitions.{class_chapters, class_preview, link_asset, link_index}
import visceljs.{Body, Make}

import scala.Predef.{$conforms, ArrowAssoc}
import scala.annotation.tailrec
import scalatags.JsDom.Frag
import scalatags.JsDom.all.Tag
import scalatags.JsDom.implicits.stringFrag
import scalatags.JsDom.tags.{SeqFrag, fieldset, header, legend}
import scalatags.JsDom.tags2.{article, section}

object Front {


	def gen(bookmark: Int, narration: Narration): Body = {

		val gallery = narration.narrates

		val top = header(s"${narration.name} ($bookmark/${gallery.size})")

		val navigation = Make.navigation(
			link_index("index"),
			link_asset(narration, gallery.first, "first"),
			Make.postBookmark(narration, 0, "remove"))

		val preview = {
			val preview3 = gallery.next(bookmark - 1)
			val preview2 = preview3.prev(1)
			val preview1 = preview2.prev(1)
			section(class_preview)(
				List(preview1, preview2, preview3).map(_.get)
					.collect { case Some(a) => link_asset(narration, preview1, Make.asset(a)) })
		}

		def chapterlist: Tag = {
			val assets = gallery.end
			val chapters = narration.chapters

			def makeChapField(chap: Chapter, size: Int, gallery: Gallery[Story.Asset]): Frag = {
				val (remaining, links) = Range(size, 0, -1).foldLeft((gallery, List[Frag]())) { case ((gal, acc), i) =>
					val next = gal.prev(1)
					(next, link_asset(narration, next, s"$i") :: acc)
				}

				article(fieldset(legend(chap.name), links))
			}


			@tailrec
			def build(apos: Int, assets: Gallery[Story.Asset], chapters: List[(Int, Story.Chapter)], acc: List[Frag]): List[Frag] = chapters match {
				case (cpos, chap) :: ctail =>
					build(cpos, assets.prev(apos - cpos), ctail, makeChapField(chap, apos - cpos, assets) :: acc)
				case Nil =>
					if (assets.pos == 0) acc
					else makeChapField(Story.Chapter("No Chapter"), assets.pos, assets) :: acc
			}

			section(class_chapters)(build(assets.pos, assets, chapters, Nil))

		}

		Body(id = "front", title = narration.name,
			frag = List(top, navigation, preview, chapterlist))

	}
}
