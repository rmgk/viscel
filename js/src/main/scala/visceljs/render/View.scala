package visceljs.render

import org.scalajs.dom
import viscel.shared.Gallery
import viscel.shared.Story.{Asset, Narration}
import visceljs.{Render, Body, Definitions}

import scala.Predef.$conforms
import scalatags.JsDom.all.{Frag, Tag}
import scalatags.JsDom.attrs.href
import scalatags.JsDom.implicits.{stringAttr, stringFrag}
import scalatags.JsDom.tags.{SeqFrag, a, div}
import scalatags.JsDom.tags2.article

object View {

	import visceljs.Definitions._
	import visceljs.Render._
	import visceljs.Actions._

	def gen(gallery: Gallery[Asset], narration: Narration): Body = {

		val handleKeypress = (ev: dom.KeyboardEvent) => {
			ev.keyCode match {
				case 37 | 65 | 188 => gotoView(gallery.prev(1), narration)
				case 39 | 68 | 190 => gotoView(gallery.next(1), narration)
				case _ =>
			}
		}

		gallery.next(1).get.map(a => blobToImg(a).render)

		def mainPart = div(class_content)(gallery.get.fold[Frag](div("error, illegal image position"))(a => link_asset(narration, gallery.next(1), blobToImg(a))))

		val navigation = List[Tag](
			link_asset(narration, gallery.prev(1), "prev"),
			link_front(narration, "front"),
			formPostBookmark(narration, gallery.pos + 1, "pause")(class_post),
			a(href := gallery.get.fold("")(_.origin.toString))(class_extern)("site"),
			link_asset(narration, gallery.next(1), "next"))

		Body(id = "view", title = narration.name,
			frag = List(
				article(class_main)(mainPart),
				Render.makeNavigation(navigation)),
			keypress = handleKeypress)
	}

}
