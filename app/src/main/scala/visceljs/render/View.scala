package visceljs.render

import org.scalajs.dom
import org.scalajs.dom.MouseEvent
import org.scalajs.dom.html.Body
import rescala.extra.Tags._
import rescala.default._
import scalatags.JsDom
import scalatags.JsDom.attrs.disabled
import scalatags.JsDom.all.{HtmlTag, Modifier, SeqFrag, Tag, a, bindJsAnyLike, body, href, id, onclick, p, rel, stringAttr, stringFrag, title}
import scalatags.JsDom.tags2.{article, section}
import viscel.shared.Log
import visceljs.Definitions.lcButton
import visceljs.Navigation._
import visceljs.{Actions, Data, Definitions, Icons}


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
        article(Snippets.asset(asst, data, Snippets.imageStyle(fitType.value)))(asst.data.get("longcomment").fold(List[Tag]())(p(_) :: Nil))
      }
    }

    val navigation: Signal[HtmlTag] = Signal {
      val data = dataSignal.value
      val ft = fitType.value
      val prev = data.prev
      val next = data.next
      if (prev.pos != data.pos) Log.JS.info(s"${prev.pos} was not ${data.pos}")
      Snippets.navigation(
        a(Icons.prev, rel := "prev", title := "previous page")(if (prev.pos == data.pos) disabled else href := Definitions.path_asset(prev)),
        a(href := Definitions.path_front(data.id), Icons.front, title := "back to front page"),
        Snippets.fullscreenToggle(Icons.maximize, title := "toggle fullscreen"),
        lcButton(navigate.fire(Mode(ft.next)), Icons.modus, s" $ft", title := "cycle image display mode"),
        act.postBookmark(data.pos + 1, data, Icons.bookmark, title := "save bookmark"),
        a(href := data.gallery.get.fold("")(_.origin), rel := "noreferrer")(Icons.externalLink, title := "visit original page"),
        a(Icons.next, rel := "next", title := "next")(if (next.pos == data.pos) disabled else href := Definitions.path_asset(next)))
    }

    val mainSection = section(mainPart.asModifier)(onLeftClickPrevNext(navigate.fire))
    Signal {body(id := "view", mainSection, navigation.asModifier)}


  }

}
