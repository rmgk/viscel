package visceljs

import org.scalajs.dom
import viscel.shared.Gallery
import viscel.shared.Story.{Asset, Narration}

import scala.Predef.conforms
import scalatags.JsDom.Frag
import scalatags.JsDom.attrs.{href, src}
import scalatags.JsDom.implicits.{stringAttr, stringFrag}
import scalatags.JsDom.tags.{SeqFrag, a, div, img}
import org.scalajs.dom
import scalatags.JsDom.tags2.{nav, article}

object ViewPage {
	import visceljs.Util._

	def gen(gallery: Gallery[Asset], narration: Narration): Frag = {

		val handleKeypress = (ev: dom.KeyboardEvent) => {
			if (!ev.altKey && !ev.ctrlKey && !ev.shiftKey && (ev.keyCode match {
					case 37 | 65 | 188 => go_view(gallery.prev(1), narration)(); true
					case 39 | 68 | 190 => go_view(gallery.next(1), narration)(); true
					case _ => false
				})) {ev.preventDefault(); true}
			else false
		}

		dom.document.onkeydown = handleKeypress

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
			article(class_main)(mainPart),
			nav(class_navigation)(navigation))
	}

}