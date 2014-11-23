package visceljs


import viscel.shared.Story.Narration

import scala.Predef.{any2ArrowAssoc, conforms}
import scalatags.JsDom.Frag
import scalatags.JsDom.implicits.stringFrag
import scalatags.JsDom.tags.{SeqFrag, div}
import scalatags.JsDom.tags2.{nav}

object FrontPage {
	import visceljs.Util._

	def genIndex(bookmark: Int, narration: Narration): Frag = {

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
			link_main("index"),
			stringFrag(" – "),
			link_asset(narration, gallery.first, "first"),
			stringFrag(" – "),
			set_bookmark(narration, 0, "remove"))

		def sidePart = div(class_content)( List(
			preview1.get.map(a => link_asset(narration, preview1, blobToImg(a))),
			preview2.get.map(a => link_asset(narration, preview2, blobToImg(a))),
			preview3.get.map(a => link_asset(narration, preview3, blobToImg(a)))
		).flatten: _*)

		def content: Frag = List(
			div(class_main)(mainPart),
			nav(class_navigation)(navigation),
			div(class_side)(sidePart))

		content
	}
}
