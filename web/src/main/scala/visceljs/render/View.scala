package visceljs.render

import org.scalajs.dom
import org.scalajs.dom.MouseEvent
import org.scalajs.dom.raw.KeyboardEvent
import rescala._
import visceljs.render.View._
import visceljs.visceltags._
import visceljs.{Actions, Body, Data, Definitions, Icons, Make}

import scalatags.JsDom.all.{Frag, HtmlTag, Modifier, SeqFrag, Tag, a, bindJsAnyLike, body, href, id, onclick, p, rel, stringAttr, stringFrag, title}
import scalatags.JsDom.tags2.{article, section}

object View {
  sealed trait Navigate
  case object Next extends Navigate
  case object Prev extends Navigate
  case class Mode(i: Int) extends Navigate
  case class Goto(data: Data) extends Navigate


  val handleKeypress = visceljs.visceltags.eventFromCallback[KeyboardEvent](dom.document.onkeydown = _)
  val navigate = Evt[Navigate]
  val keypressNavigations = handleKeypress.map(_.key).collect {
    case "ArrowLeft" | "a" | "," => Prev
    case "ArrowRight" | "d" | "." => Next
    case n if n.matches("""^\d+$""") => Mode(n.toInt)
  }

  val navigationEvents: Event[Navigate] = keypressNavigations || navigate



//  navigationEvents.map(e => e -> dataSignal()).observe { case (ev, data) =>
//    if (ev == Prev || ev == Next) {
//      act.scrollTop()
//    }
//    /*val pregen =*/ data.gallery.next(1).get.map(asst => div(Make.asset(asst, data)).render)
//
//  }

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

  def gen(dataSignal: Signal[Data], navigate: Evt[Navigate]): Body = {

    val mainPart: Signal[Frag] = dataSignal.map[HtmlTag] { data =>
      data.gallery.get.fold[HtmlTag](p(s"loading image data …")) { asst =>
        article(Make.asset(asst, data, Make.imageStyle(data.fitType)))(asst.data.get("longcomment").fold(List[Tag]())(p(_) :: Nil))
      }
    }

    val mainSection = section(mainPart.asFrag)(onLeftClickPrevNext(navigate.fire))

    val navigation: Frag =
      dataSignal.map[Frag] { data =>
        Make.navigation(
          act.Tags.button_asset(data.prev, navigate.fire(Prev))(Icons.prev, rel := "prev", title := "previous page"),
          act.Tags.lcButton(act.gotoFront(dataSignal, data.description))(Icons.front, title := "back to front page"),
          Make.fullscreenToggle(Icons.maximize, title := "toggle fullscreen"),
          act.Tags.lcButton(navigate.fire(Mode(data.fitType + 1)), Icons.modus, s" ${data.fitType % 8}", title := "cycle image display mode"),
          act.Tags.postBookmark(data.pos + 1, data, d => navigate.fire(Goto(d)), Icons.bookmark, title := "save bookmark"),
          a(Definitions.class_button, href := data.gallery.get.fold("")(_.origin))(Icons.externalLink, title := "visit original page"),
          act.Tags.button_asset(data.next, navigate.fire(Next))(Icons.next, rel := "next", title := "next"))
      }.asFrag

    Body(id = "view", title = dataSignal.map(data => s"${data.pos + 1} – ${data.description.name}"),
         bodyTag = Signal{body(id := "view", mainSection, navigation)})


  }

}
