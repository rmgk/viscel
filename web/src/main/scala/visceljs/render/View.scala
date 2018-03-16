package visceljs.render

import org.scalajs.dom
import org.scalajs.dom.MouseEvent
import rescala._
import rescalatags._
import visceljs.render.View._
import visceljs.{Actions, Body, Data, Definitions, Icons, Make}

import scalatags.JsDom.all.{Frag, HtmlTag, Modifier, SeqFrag, Tag, a, bindJsAnyLike, div, href, onclick, p, rel, stringAttr, stringFrag, title}
import scalatags.JsDom.tags2.{article, section}

object View {
  sealed trait Navigate
  case object Next extends Navigate
  case object Prev extends Navigate
  case class Mode(i: Int) extends Navigate
  case class Goto(data: Data) extends Navigate
}


class View(act: Actions) {

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
        act.pushView(data)
        act.scrollTop()
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
          act.Tags.button_asset(data.prev, navigate.fire(Prev))(Icons.prev, rel := "prev", title := "previous page"),
          act.Tags.button_front(data.description, Icons.front, title := "back to front page"),
          Make.fullscreenToggle(Icons.maximize, title := "toggle fullscreen"),
          act.Tags.lcButton(navigate.fire(Mode(data.fitType + 1)), Icons.modus, s" ${data.fitType % 8}", title := "cycle image display mode"),
          act.Tags.postBookmark(data.pos + 1, data, d => navigate.fire(Goto(d)), Icons.bookmark, title := "save bookmark"),
          a(Definitions.class_button, href := data.gallery.get.fold("")(_.origin))(Icons.externalLink, title := "visit original page"),
          act.Tags.button_asset(data.next, navigate.fire(Next))(Icons.next, rel := "next", title := "next"))
      }.asFrag

    Body(id = "view", title = dataSignal.map(data => s"${data.pos + 1} – ${data.description.name}"),
         frag = List(mainSection, navigation),
         keypress = (x: dom.KeyboardEvent) => handleKeypress.fire(x))


  }

}
