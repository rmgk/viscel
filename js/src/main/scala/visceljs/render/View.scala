package visceljs.render

import org.scalajs.dom
import viscel.shared.Gallery
import viscel.shared.Story.{Asset, Narration}
import visceljs.Definitions.{class_extern, link_asset, link_front}
import visceljs.{Viscel, Actions, Body, Make}

import scala.Predef.$conforms
import scalatags.JsDom.all._
import scalatags.JsDom.attrs.{href, rel, onclick}
import scalatags.JsDom.implicits.{stringAttr, stringFrag}
import scalatags.JsDom.tags.{SeqFrag, a, p}
import scalatags.JsDom.tags2.section

object View {


	def gen(gallery: Gallery[Asset], narration: Narration): Body = gallery.get match {
		case None => Body("illegal position", "error", "error")
		case Some(current) =>

			val handleKeypress = (ev: dom.KeyboardEvent) => {
				ev.keyCode match {
					case 37 | 65 | 188 if !gallery.isFirst => Actions.gotoView(gallery.prev(1), narration)
					case 39 | 68 | 190 if !gallery.next(1).isEnd => Actions.gotoView(gallery.next(1), narration)
					case _ =>
				}
			}

			val preload = gallery.next(1).get.map(Make.asset(_).render)

			val mainPart = section(gallery.get.fold[Frag](p("error, illegal image position"))(a => link_asset(narration, gallery.next(1), Make.asset(a))))

			val navigation = Make.navigation(
				link_asset(narration, gallery.prev(1), "prev")(rel := "prev"),
				link_front(narration, "front"),
				Make.fullscreenToggle("TFS"),
				Make.postBookmark(narration, gallery.pos + 1, "pause"),
				a(href := gallery.get.fold("")(_.origin.toString))(class_extern)("site"),
				link_asset(narration, gallery.next(1), "next")(rel := "next"))

			Body(id = "view", title = narration.name,
				frag = List(mainPart, navigation),
				keypress = handleKeypress)
	}

}
