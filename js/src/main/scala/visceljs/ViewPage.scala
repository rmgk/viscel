package visceljs

import viscel.shared.Gallery
import viscel.shared.Story.{Asset, Narration}

import scala.scalajs.js
import scalatags.JsDom.attrs.id
import scalatags.JsDom.Frag
import scala.Predef.conforms
import scalatags.JsDom.short.{HtmlTag}
import scalatags.JsDom.tags.{div, body, SeqFrag, input, a}
import scalatags.JsDom.attrs.{`type`, name, value, href}
import scalatags.JsDom.implicits.{stringFrag, stringAttr}
import scala.Predef.???
import scala.Predef.any2ArrowAssoc

object ViewPage {
	import Util._

	def gen(gallery: Gallery[Asset], narration: Narration): Frag = {

		def mainPart = div(class_content)(link_asset(narration, gallery.next(1), blobToImg(gallery.get.get))) :: Nil

		val navigation = Seq[Frag](
			link_asset(narration, gallery.prev(1), "prev"),
			" ",
			link_front(narration, "front"),
			" ",
//			form_post(path_nid(collection),
//				input(`type` := "hidden", name := "collection", value := collection.id),
//				input(`type` := "hidden", name := "bookmark", value := pos),
//				input(`type` := "submit", name := "submit", value := "pause", class_submit)),
			" ",
			a(href := gallery.get.get.origin.toString)(class_extern)("site"),
			" ",
			link_asset(narration, gallery.next(1), "next"))

		List(
			div(class_main)(mainPart),
			div(class_navigation)(navigation))
	}

}
