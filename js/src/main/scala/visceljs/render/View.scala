package visceljs.render

import org.scalajs.dom
import viscel.shared.Gallery
import viscel.shared.Story.{Asset, Narration}
import visceljs.{Actions, Body, Definitions, Make}

import scala.Predef.$conforms
import scalatags.JsDom.all.{Frag, Tag}
import scalatags.JsDom.attrs.href
import scalatags.JsDom.implicits.{stringAttr, stringFrag}
import scalatags.JsDom.tags.{SeqFrag, a, div}
import scalatags.JsDom.tags2.article

object View {

	import visceljs.Definitions._

	def gen(gallery: Gallery[Asset], narration: Narration): Body = {

		val handleKeypress = (ev: dom.KeyboardEvent) => {
			ev.keyCode match {
				case 37 | 65 | 188 => Actions.gotoView(gallery.prev(1), narration)
				case 39 | 68 | 190 => Actions.gotoView(gallery.next(1), narration)
				case _ =>
			}
		}

		gallery.next(1).get.map(a => Make.asset(a).render)

		def mainPart = div(class_content)(gallery.get.fold[Frag](div("error, illegal image position"))(a => link_asset(narration, gallery.next(1), Make.asset(a))))

		val navigation = List[Tag](
			link_asset(narration, gallery.prev(1), "prev"),
			link_front(narration, "front"),
			Make.formPostBookmark(narration, gallery.pos + 1, "pause")(class_post),
			a(href := gallery.get.fold("")(_.origin.toString))(class_extern)("site"),
			link_asset(narration, gallery.next(1), "next"))

		Body(id = "view", title = narration.name,
			frag = List(
				article(class_main)(mainPart),
				Make.navigation(navigation)),
			keypress = handleKeypress)
	}

}
