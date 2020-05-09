package visceljs.render

import org.scalajs.dom
import org.scalajs.dom.MouseEvent
import org.scalajs.dom.html.Body
import rescala.extra.Tags._
import rescala.default._
import scalatags.JsDom
import scalatags.JsDom.all.{HtmlTag, Modifier, SeqFrag, Tag, a, bindJsAnyLike, body, href, id, onclick, p, rel, stringAttr, stringFrag, title}
import scalatags.JsDom.tags2.{article, section}
import visceljs.Definitions.lcButton
import visceljs.Navigation._
import visceljs.{Actions, Data, FitType, Icons, Make}


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

  def gen(dataSignal: Signal[Data], fitType: Signal[FitType], navigate: Evt[Navigate]): Signal[JsDom.TypedTag[Body]] = {

    val mainPart: Signal[HtmlTag] = Signal {
      val data = dataSignal.value
      data.gallery.get.fold[HtmlTag](p(s"loading image data …")) { asst =>
        article(Make.asset(asst, data, Make.imageStyle(fitType.value)))(asst.data.get("longcomment").fold(List[Tag]())(p(_) :: Nil))
      }
    }

    val navigation: Signal[HtmlTag] = Signal {
      val data = dataSignal.value
      val ft = fitType.value
        Make.navigation(
          act.button_asset(data.prev, navigate.fire(Prev))(Icons.prev, rel := "prev", title := "previous page"),
          act.link_front(data.id, Icons.front, title := "back to front page"),
          Make.fullscreenToggle(Icons.maximize, title := "toggle fullscreen"),
          lcButton(navigate.fire(Mode(ft.next)), Icons.modus, s" $ft", title := "cycle image display mode"),
          act.postBookmark(data.pos + 1, data, _ => (), Icons.bookmark, title := "save bookmark"),
          a(href := data.gallery.get.fold("")(_.origin), rel := "noreferrer")(Icons.externalLink, title := "visit original page"),
          act.button_asset(data.next, navigate.fire(Next))(Icons.next, rel := "next", title := "next"))
      }

    val mainSection = section(mainPart.asModifier)(onLeftClickPrevNext(navigate.fire))
    Signal {body(id := "view", mainSection, navigation.asModifier)}


  }

}
