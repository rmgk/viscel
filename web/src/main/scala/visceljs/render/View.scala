package visceljs.render

import org.scalajs.dom
import org.scalajs.dom.MouseEvent
import org.scalajs.dom.html.Body
import rescala.default._
import visceljs.Navigation._
import visceljs.visceltags._
import visceljs.{Actions, Data, Definitions, Icons, Make}

import scalatags.JsDom
import scalatags.JsDom.all.{Frag, HtmlTag, Modifier, SeqFrag, Tag, a, bindJsAnyLike, body, href, id, onclick, p, rel, stringAttr, stringFrag, title}
import scalatags.JsDom.tags2.{article, section}


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

  def gen(dataSignal: Signal[Data], fitType: Signal[Int], navigate: Evt[Navigate]): Signal[JsDom.TypedTag[Body]] = {

    val mainPart: Signal[Frag] = Signal {
      val data = dataSignal.value
      data.gallery.get.fold[HtmlTag](p(s"loading image data …")) { asst =>
        article(Make.asset(asst, data, Make.imageStyle(fitType.value)))(asst.data.get("longcomment").fold(List[Tag]())(p(_) :: Nil))
      }
    }

    val navigation: Signal[Frag] = Signal {
      val data = dataSignal.value
      val ft = fitType.value
        Make.navigation(
          act.button_asset(data.prev, navigate.fire(Prev))(Icons.prev, rel := "prev", title := "previous page"),
          act.lcButton(act.gotoFront(data.description))(Icons.front, title := "back to front page"),
          Make.fullscreenToggle(Icons.maximize, title := "toggle fullscreen"),
          act.lcButton(navigate.fire(Mode(ft + 1)), Icons.modus, s" $ft", title := "cycle image display mode"),
          act.postBookmark(data.pos + 1, data, _ => Unit, Icons.bookmark, title := "save bookmark"),
          a(Definitions.class_button, href := data.gallery.get.fold("")(_.origin), rel := "noreferrer")(Icons.externalLink, title := "visit original page"),
          act.button_asset(data.next, navigate.fire(Next))(Icons.next, rel := "next", title := "next"))
      }

    val mainSection = section(mainPart.asFrag)(onLeftClickPrevNext(navigate.fire))
    Signal {body(id := "view", mainSection, navigation.asFrag)}


  }

}
