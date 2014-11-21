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

	def genIndex(bookmark: Int, collection: String, collectionName: String, narration: Narration): Frag = {

		val assetList = null.asInstanceOf[List[Asset]]

		lazy val first = assetList(0)
		lazy val second = assetList(1)
		lazy val third = assetList(2)
		lazy val previewLeft = assetList(bookmark - 2)
		lazy val previewMiddle = assetList(bookmark - 1)
		lazy val previewRight = assetList(bookmark)

		def mainPart = div(class_info)(
			make_table(
				"id" -> collection,
				"name" -> collectionName
			)) :: Nil

		def navigation = Seq[Frag](
			link_main("index"),
			stringFrag(" – "),
			link_asset(collection, 0, "first"),
			stringFrag(" – "),
			"TODO: remove")

		def sidePart = div(class_content)(
					link_asset(collection, bookmark - 2, blobToImg(previewLeft)),
					link_asset(collection, bookmark - 1, blobToImg(previewMiddle)),
					link_asset(collection, bookmark, blobToImg(previewRight)))

		def content: Frag = List(
			div(class_main)(mainPart),
			div(class_navigation)(navigation: _*),
			div(class_side)(sidePart))

		content
	}
}
