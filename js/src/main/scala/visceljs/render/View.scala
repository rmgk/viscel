package visceljs.render

import org.scalajs.dom
import viscel.shared.Gallery
import viscel.shared.Story.{Content, Asset, Description}
import visceljs.Definitions.{class_dead, class_extern, link_asset, link_front}
import visceljs.{Data, Viscel, Actions, Body, Make}

import scala.Predef.$conforms
import scalatags.JsDom.all._
import scalatags.JsDom.attrs.{href, rel, onclick}
import scalatags.JsDom.implicits.{stringAttr, stringFrag}
import scalatags.JsDom.tags.{SeqFrag}
import scalatags.JsDom.tags2.{section, article}

object View {


	def gen(data: Data): Body = {
		val Data(narration: Description, Content(gallery: Gallery[Asset], _), bookmark: Int) = data
		gallery.get match {
			case None => Body("illegal position", "error", "error")
			case Some(current) =>

				val handleKeypress = (ev: dom.KeyboardEvent) => {
					ev.keyCode match {
						case 37 | 65 | 188 if !gallery.isFirst => Actions.gotoView(data.prev)
						case 39 | 68 | 190 if !gallery.next(1).isEnd => Actions.gotoView(data.next)
						case _ =>
					}
				}

				val preload = gallery.next(1).get.map(asst => div(Make.asset(asst)).render)

				val mainPart = section(gallery.get.fold[Frag](p("error, illegal image position")){asst =>
					article(link_asset(data.next)(Make.asset(asst))) ::
						asst.metadata.get("longcomment").fold(List[Tag]())(article(_) :: Nil)
				})


				val navigation = Make.navigation(
					link_asset(data.prev)("prev", rel := "prev"),
					link_front(narration, "front"),
					Make.fullscreenToggle("TFS"),
					if (bookmark != gallery.pos + 1) Make.postBookmark(narration, gallery.pos + 1, "pause") else a(class_dead, "pause"),
					a(href := gallery.get.fold("")(_.origin.toString))(class_extern)("site"),
					link_asset(data.next)("next", rel := "next"))

				Body(id = "view", title = narration.name,
					frag = List(mainPart, navigation),
					keypress = handleKeypress)
		}
	}

}
