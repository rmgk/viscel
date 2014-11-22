package visceljs

import viscel.shared.Gallery
import viscel.shared.Story.{Asset, Narration}

import scala.Predef.conforms
import scalatags.JsDom.Frag
import scalatags.JsDom.attrs.{href, src}
import scalatags.JsDom.implicits.{stringAttr, stringFrag}
import scalatags.JsDom.tags.{SeqFrag, a, div, img}

object ViewPage {
	import visceljs.Util._

	def gen(gallery: Gallery[Asset], narration: Narration): Frag = {

		gallery.next(1).get.map(a => blobToImg(a).render)

		def mainPart = div(class_content)(link_asset(narration, gallery.next(1), blobToImg(gallery.get.get))) :: Nil

		val navigation = Seq[Frag](
			link_asset(narration, gallery.prev(1), "prev"),
			" ",
			link_front(narration, "front"),
			" ",
			set_bookmark(narration, gallery.pos + 1, "pause")(class_submit),
			" ",
			a(href := gallery.get.get.origin.toString)(class_extern)("site"),
			" ",
			link_asset(narration, gallery.next(1), "next"))

		List(
			div(class_main)(mainPart),
			div(class_navigation)(navigation))
	}

}
