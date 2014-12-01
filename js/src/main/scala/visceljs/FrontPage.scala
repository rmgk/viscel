package visceljs


import viscel.shared.Story.{Chapter, Narration}
import viscel.shared.{Gallery, Story}

import scala.Predef.{ArrowAssoc, $conforms}
import scalatags.JsDom
import scalatags.JsDom.Frag
import scalatags.JsDom.attrs.cls
import scalatags.JsDom.implicits.{stringAttr, stringFrag}
import scalatags.JsDom.tags.{SeqFrag, div, fieldset, legend}
import scalatags.JsDom.tags2.nav

object FrontPage {

	import visceljs.Util._

	def gen(bookmark: Int, narration: Narration): Body = {

		val gallery = narration.narrates

		val preview3 = gallery.next(bookmark - 1)
		val preview2 = preview3.prev(1)
		val preview1 = preview2.prev(1)

		def mainPart = div(class_info)(
			make_table(
				"id" -> narration.id,
				"name" -> narration.name
			)) :: Nil

		def navigation = Seq[Frag](
			link_index("index"),
			stringFrag(" – "),
			link_asset(narration, gallery.first, "first"),
			stringFrag(" – "),
			set_bookmark(narration, 0, "remove"))

		def sidePart = div(class_content)(List(
			preview1.get.map(a => link_asset(narration, preview1, blobToImg(a))),
			preview2.get.map(a => link_asset(narration, preview2, blobToImg(a))),
			preview3.get.map(a => link_asset(narration, preview3, blobToImg(a)))
		).flatten: _*)

		def chapterlist: List[JsDom.Frag] = {
			val assets = gallery.end
			val chapters = narration.chapters

			def makeChapField(chap: Chapter, size: Int, gallery: Gallery[Story.Asset]): Frag = {
				val (remaining, links) = Range(size, 0, -1).foldLeft((gallery, List[Frag]())) { case ((gal, acc), i) =>
					val next = gal.prev(1)
					(next, link_asset(narration, next, s"$i ") :: acc)
				}

				/** for some reason, setting multiple classes does no longer work, keep first to for code refactorings */
				fieldset(class_group, class_pages, cls := "group pages").apply(legend(chap.name), links)
			}


			def build(apos: Int, assets: Gallery[Story.Asset], chapters: List[(Int, Story.Chapter)], acc: List[Frag]): List[Frag] = chapters match {
				case (cpos, chap) :: ctail =>
					build(cpos, assets.prev(apos - cpos), ctail, makeChapField(chap, apos - cpos, assets) :: acc)
				case Nil =>
					if (assets.pos == 0) acc
					else makeChapField(Story.Chapter("No Chapter"), assets.pos, assets) :: acc
			}

			build(assets.pos, assets, chapters, Nil)

		}

		def content: Frag = List(
			div(class_main)(mainPart),
			nav(class_navigation)(navigation),
			div(class_side)(sidePart)
		) ++ chapterlist

		Body(id = "front", frag = content, title = narration.name)
	}
}
