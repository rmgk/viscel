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

		val assets = narration.narrates

		val preview = assets.drop(bookmark - 3) ::: assets

		def mainPart = div(class_info)(
			make_table(
				"id" -> narration.id,
				"name" -> narration.name
			)) :: Nil

		def navigation = Seq[Frag](
			link_main("index"),
			stringFrag(" – "),
			link_asset(narration, 1, "first"),
			stringFrag(" – "),
			"TODO: remove")

		def sidePart = div(class_content)( List(
			preview.headOption.map(a => link_asset(narration, bookmark - 2, blobToImg(a))),
			preview.drop(1).headOption.map(a => link_asset(narration, bookmark - 1, blobToImg(a))),
			preview.drop(2).headOption.map(a => link_asset(narration, bookmark - 0, blobToImg(a)))
		).flatten: _*)

		def content: Frag = List(
			div(class_main)(mainPart),
			div(class_navigation)(navigation),
			div(class_side)(sidePart))

		content
	}
}
