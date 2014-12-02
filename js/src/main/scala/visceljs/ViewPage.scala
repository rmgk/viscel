package visceljs

import org.scalajs.dom
import viscel.shared.Gallery
import viscel.shared.Story.{Asset, Narration}

import scala.Predef.$conforms
import scalatags.JsDom.all.{Tag, Frag}
import scalatags.JsDom.attrs.{href, style}
import scalatags.JsDom.styles.{width, padding}
import scalatags.JsDom.implicits.{stringAttr, stringFrag, stringStyle}
import scalatags.JsDom.tags.{SeqFrag, a, div, span}
import scalatags.JsDom.tags2.{article, nav}

object ViewPage {

	import visceljs.Util._

	def gen(gallery: Gallery[Asset], narration: Narration): Body = {

		val handleKeypress = (ev: dom.KeyboardEvent) => {
			ev.keyCode match {
				case 37 | 65 | 188 => pushView(gallery.prev(1), narration)
				case 39 | 68 | 190 => pushView(gallery.next(1), narration)
				case _ =>
			}
		}

		gallery.next(1).get.map(a => blobToImg(a).render)

		def mainPart = div(class_content)(gallery.get.fold[Frag](div("error, illegal image position"))(a => link_asset(narration, gallery.next(1), blobToImg(a))))

		val navigation = List[Tag](
			link_asset(narration, gallery.prev(1), "prev"),
			link_front(narration, "front"),
			set_bookmark(narration, gallery.pos + 1, "pause")(class_submit),
			a(href := gallery.get.fold("")(_.origin.toString))(class_extern)("site"),
			link_asset(narration, gallery.next(1), "next"))

		Body(id = "view", title = narration.name,
			frag = List(
				article(class_main)(mainPart),
				Util.makeNavigation(navigation)),
			keypress = handleKeypress)
	}

}
