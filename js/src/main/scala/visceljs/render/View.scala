package visceljs.render

import org.scalajs.dom
import org.scalajs.dom.MouseEvent
import org.scalajs.dom.ext.KeyCode
import rescala._
import rescalatags._
import visceljs.Actions.gotoView
import visceljs.Definitions.{class_dead, class_extern, class_post, link_asset, link_front}
import visceljs.Make.postBookmark
import visceljs.{Actions, Body, Data, Make}

import scalatags.JsDom.all.{Frag, HtmlTag, Modifier, SeqFrag, SeqNode, Tag, a, bindJsAnyLike, div, href, onclick, p, rel, span, stringAttr, stringFrag}
import scalatags.JsDom.tags2.{article, section}

object View {

	sealed trait Navigate
	case object Next extends Navigate
	case object Prev extends Navigate
	case object Stay extends Navigate
	case class Mode(i: Int) extends Navigate

	def onLeftClickPrevNext(handler: Navigate => Unit): Modifier = onclick := { (e: MouseEvent) =>
		val node = e.currentTarget.asInstanceOf[dom.html.Element]
		if (e.button == 0) {
			e.preventDefault()
			val relx = e.clientX - node.offsetLeft
			val border = math.max(node.offsetWidth / 10, 100)
			if (relx < border) handler(Prev)
			else handler(Next)
		}
	}

	def gen(initialData: Data): Body = {

		val handleKeypress = Evt[dom.KeyboardEvent]
		val navigate = Evt[Navigate]

		val navigationEvents = handleKeypress.map { ev =>
			ev.keyCode match {
				case KeyCode.Left | KeyCode.A | 188 => Prev
				case KeyCode.Right | KeyCode.D | 190 => Next
				case n if KeyCode.Num0 <= n && n <= KeyCode.Num9 => Mode(n - KeyCode.Num0)
				case _ => Stay
			}
		}.||(navigate)

		val dataSignal = navigationEvents.fold(initialData) { (data, ev) =>
			ev match {
				case Prev if !data.gallery.isFirst => data.prev
				case Next if !data.gallery.next(1).isEnd => data.next
				case Mode(n) => data.copy(fitType = n)
				case Stay => data
			}
		}

		dataSignal.change.observe { case (old, now) =>
			Actions.pushView(now)
			if (old.gallery.pos != now.gallery.pos) Actions.scrollTop()
			val pregen = now.gallery.next(1).get.map(asst => div(Make.asset(asst, initialData)).render)
		}

		val mainPart: Signal[HtmlTag] = dataSignal.map[HtmlTag] { data =>
			data.gallery.get.fold[HtmlTag](p("error, illegal image position")) { asst =>
				article(Make.asset(asst, data))(asst.data.get("longcomment").fold(List[Tag]())(p(_) :: Nil))
			}
		}

		lazy val mainSection = section(mainPart.asFragment)(onLeftClickPrevNext(navigate.fire))

		val navigation: Frag =
			dataSignal.map { data => Make.navigation(
					link_asset(data.prev, navigate(Prev))("prev", rel := "prev"),
					link_front(initialData.description, "front"),
					Make.fullscreenToggle("TFS"),
					a(s"mode(${data.fitType % 8})", class_post, onclick := { () => navigate(Mode(data.fitType + 1)) }),
					if (data.bookmark != data.pos + 1) postBookmark(data.description, data.pos + 1, data, gotoView(_, scrolltop = false), "pause") else span(class_dead, "pause"),
					a(href := data.gallery.get.fold("")(_.origin))(class_extern)("site"),
					link_asset(data.next, navigate(Next))("next", rel := "next"))
				}.asFragment

		initialData.gallery.get match {
			case None => Body("illegal position", "error", "error")
			case Some(_) =>
				Body(id = "view", title = initialData.description.name,
					frag = List(mainSection, navigation),
					keypress = (x: dom.KeyboardEvent) => handleKeypress.fire(x))
		}


	}

}
