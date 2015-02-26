package visceljs.render

import org.scalajs.dom
import org.scalajs.dom.html.Element
import viscel.shared.{Article, Content, Description, Gallery}
import visceljs.Actions.{gotoView, onLeftClickPrevNext}
import visceljs.Definitions.{class_dead, class_extern, class_post, link_asset, link_front}
import visceljs.Make.postBookmark
import visceljs.{Body, Data, Make}

import scala.Predef.$conforms
import scalatags.JsDom.all._
import scalatags.JsDom.attrs.{href, rel}
import scalatags.JsDom.implicits.{stringAttr, stringFrag}
import scalatags.JsDom.tags.SeqFrag
import scalatags.JsDom.tags2.{article, section}

object View {


	def gen(data: Data): Body = {
		val Data(narration: Description, Content(gallery: Gallery[Article], _), bookmark: Int, _) = data
		gallery.get match {
			case None => Body("illegal position", "error", "error")
			case Some(current) =>

				val next = data.next
				val prev = data.prev


				val handleKeypress = (ev: dom.KeyboardEvent) => {
					ev.keyCode match {
						case 37 | 65 | 188 if !gallery.isFirst => gotoView(prev)
						case 39 | 68 | 190 if !gallery.next(1).isEnd => gotoView(next)
						case n if 48 <= n && n <= 55 => gotoView(data.copy(fitType = n - 48), scrolltop = false)
						case _ =>
					}
				}

				val preload = gallery.next(1).get.map(asst => div(Make.asset(asst, data)).render)


				lazy val mainPart: Element = section(gallery.get.fold[Frag](p("error, illegal image position")) { asst =>
					article(Make.asset(asst, data))(asst.data.get("longcomment").fold(List[Tag]())(p(_) :: Nil))
				})(onLeftClickPrevNext(mainPart, data)).render


				val navigation = Make.navigation(
					link_asset(prev)("prev", rel := "prev"),
					link_front(narration, "front"),
					Make.fullscreenToggle("TFS"),
					a(s"mode(${data.fitType % 8})", class_post, onclick := {() => gotoView(data.copy(fitType = data.fitType + 1))}),
					if (bookmark != gallery.pos + 1) postBookmark(narration, data.pos + 1, data, gotoView(_, scrolltop = false), "pause") else span(class_dead, "pause"),
					a(href := gallery.get.flatMap(_.origin).getOrElse(""))(class_extern)("site"),
					link_asset(next)("next", rel := "next"))

				Body(id = "view", title = narration.name,
					frag = List(bindNode(mainPart), navigation),
					keypress = handleKeypress)
		}
	}

}
