package visceljs


import viscel.shared.Story.{Asset, Narration}

import scala.scalajs.js
import scalatags.JsDom.attrs.id
import scalatags.JsDom.Frag
import scala.Predef.conforms
import scalatags.JsDom.short.{HtmlTag}
import scalatags.JsDom.tags.{div, body, SeqFrag, input}
import scalatags.JsDom.attrs.{`type`, name, value}
import scalatags.JsDom.implicits.{stringFrag, stringAttr}
import scala.Predef.???
import scala.Predef.any2ArrowAssoc

object FrontPage {
	import Util._

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
			"TODO: remove")

		def sidePart = div(class_content)( List(
			preview1.get.map(a => link_asset(narration, preview1, blobToImg(a))),
			preview2.get.map(a => link_asset(narration, preview2, blobToImg(a))),
			preview3.get.map(a => link_asset(narration, preview3, blobToImg(a)))
		).flatten: _*)

		def content: Frag = List(
			div(class_main)(mainPart),
			div(class_navigation)(navigation),
			div(class_side)(sidePart))

		content
	}
}
