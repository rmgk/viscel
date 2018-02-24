package visceljs.render

import org.scalajs.dom
import org.scalajs.dom.MouseEvent
import rescala._
import rescalatags._
import visceljs.Definitions.{button_asset, button_front}
import visceljs.Make.postBookmark
import visceljs.{Actions, Body, Data, Definitions, Make}

import scalatags.JsDom.all.{Frag, HtmlTag, Modifier, SeqFrag, Tag, a, bindJsAnyLike, div, href, onclick, p, rel, stringAttr, stringFrag}
import scalatags.JsDom.tags2.{article, section}

object View {

  sealed trait Navigate
  case object Next extends Navigate
  case object Prev extends Navigate
  case class Mode(i: Int) extends Navigate
  case class Goto(data: Data) extends Navigate

  def onLeftClickPrevNext(handler: Navigate => Unit): Modifier = onclick := { (e: MouseEvent) =>
    val node = e.currentTarget.asInstanceOf[dom.html.Element]
    if (e.button == 0 && dom.document.getSelection().isCollapsed && dom.window.getSelection().isCollapsed) {
      e.preventDefault()
      val relx = e.clientX - node.offsetLeft
      val border = math.max(node.offsetWidth / 10, 100)
      if (relx < border) handler(Prev)
      else handler(Next)
    }
  }

  def gen(outerNavigation: Event[Navigate]): Body = {

    val handleKeypress = Evt[dom.KeyboardEvent]
    val navigate = Evt[Navigate]
    val keypressNavigations = handleKeypress.map(_.key).collect {
      case "ArrowLeft" | "a" | "," => Prev
      case "ArrowRight" | "d" | "." => Next
      case n if n.matches("""^\d+$""") => Mode(n.toInt)
    }

    val navigationEvents: Event[Navigate] = keypressNavigations || navigate || outerNavigation

    val dataSignal: Signal[Data] = navigationEvents.reduce[Data] { (data, ev) =>
      ev match {
        case Prev if !data.gallery.isFirst => data.prev
        case Next if !data.gallery.next(1).isEnd => data.next
        case Prev | Next => data
        case Mode(n) => data.copy(fitType = n)
        case Goto(target) => target
      }
    }

    Event {navigationEvents().map(e => e -> dataSignal())}.observe { case (ev, data) =>
      if (ev == Prev || ev == Next) {
        Actions.pushView(data)
        Actions.scrollTop()
      }
      /*val pregen =*/ data.gallery.next(1).get.map(asst => div(Make.asset(asst, data)).render)

    }

    val mainPart: Signal[HtmlTag] = dataSignal.map[HtmlTag] { data =>
      data.gallery.get.fold[HtmlTag](p(s"loading image data …")) { asst =>
        article(Make.asset(asst, data, Make.imageStyle(data.fitType)))(asst.data.get("longcomment").fold(List[Tag]())(p(_) :: Nil))
      }
    }

    lazy val mainSection = section(mainPart.asFrag)(onLeftClickPrevNext(navigate.fire))

    val navigation: Frag =
      dataSignal.map { data =>
        Make.navigation(
          button_asset(data.prev, navigate.fire(Prev))("⎗", rel := "prev"),
          button_front(data.description, "\uD83D\uDDD0"),
          Make.fullscreenToggle("\uD83D\uDDD6"),
          Make.lcButton(navigate.fire(Mode(data.fitType + 1)), s"🖵 ${data.fitType % 8}"),
          postBookmark(data.pos + 1, data, d => navigate.fire(Goto(d)), "\uD83D\uDD16"),
          a(Definitions.class_button, href := data.gallery.get.fold("")(_.origin))("\uD83D\uDD78"),
          button_asset(data.next, navigate.fire(Next))("⎘", rel := "next"))
      }.asFrag

    Body(id = "view", title = dataSignal.map(data => s"${data.pos + 1} – ${data.description.name}"),
         frag = List(mainSection, navigation),
         keypress = (x: dom.KeyboardEvent) => handleKeypress.fire(x))


  }

}
