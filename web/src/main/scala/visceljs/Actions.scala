package visceljs

import org.scalajs.dom
import org.scalajs.dom.MouseEvent
import rescala._
import viscel.shared.Description
import visceljs.Definitions.{class_button, class_button_disabled, path_asset, path_front, path_main}
import visceljs.render.View

import scalatags.JsDom.all.{HtmlTag, Modifier, Tag, a, bindJsAnyLike, button, href, onclick}


class Actions(app: ReaderApp) {

  val viewE = Evt[View.Navigate]
//  val viewDispatchChangeE = Signal.dynamic {
//    val nars = app.descriptions()
//    val (id, pos) = ("VD_TwoKinds", 1)
//    val nar = nars.getOrElse(id, throw EmptySignalControlThrowable)
//    val content = app.content(nar): @unchecked
//    val bm = app.bookmarks().getOrElse(nar.id, 0)
//    View.Goto(Data(nar, content(), bm).move(_.first.next(pos - 1)))
//  }.changed



  lazy val viewBody = app.view.gen(viewE)

  def scrollTop() = dom.window.scrollTo(0, 0)

  def pushIndex(): Unit = dom.window.history.pushState(null, "main", path_main)
  def pushFront(nar: Description): Unit = dom.window.history.pushState(null, "front", path_front(nar))
  def pushView(data: Data): Unit = dom.window.history.pushState(null, "view", path_asset(data))

  def onLeftClick(a: => Unit): Modifier = onclick := { (e: MouseEvent) =>
    if (e.button == 0) {
      e.preventDefault()
      a
    }
  }

  def gotoIndex(): Unit = {
    //pushIndex()
    app.manualStates.fire(app.IndexState)
    //setBodyIndex(scrolltop = true)
  }

  def gotoFront(desc: Description): Unit = {
    gotoFront(app.getDataSignal(desc.id), desc)
  }
  def gotoFront(data: Signal[Data], description: Description, scrolltop: Boolean = false): Unit = {
    //pushFront(description)
    app.manualStates.fire(app.FrontState(data))
  }

  def gotoView(data: Data, scrolltop: Boolean = true): Unit = {
    //pushView(data)
    setBodyView(data, scrolltop)
    app.manualStates.fire(app.ViewState(Signal{data}))
  }


  def setBodyView(data: Data, scrolltop: Boolean = false): Unit = {
    viewE.fire(View.Goto(data))
    app.setBody(viewBody, scrolltop)
  }

  object Tags {
    def button_index(ts: Modifier*): Tag = button(class_button, onLeftClick(gotoIndex()))(ts: _*)
    def link_asset(data: Data): Tag = a.apply(onLeftClick(gotoView(data)), href := path_asset(data))
    def button_asset(data: Data): Tag = button_asset(data, gotoView(data))
    def button_asset(data: Data, onleft: => Unit): Tag = {
      if (data.gallery.isEnd) button(class_button_disabled)
      else lcButton(onleft)
    }

    def link_front(nar: Description, ts: Modifier*): Tag = a(onLeftClick(gotoFront(nar)), href := path_front(nar))(ts: _*)

    def postBookmark(bm: Int, data: Data, handler: Data => Unit, ts: Modifier*): HtmlTag = {
      if (data.bookmark != bm) {
        lcButton {
          app.postBookmark(data.description, bm)
          handler(data.copy(bookmark = bm))
        }(ts: _*)
      }
      else {
        button(class_button_disabled)(ts: _*)
      }
    }

    def postForceHint(nar: Description, ts: Modifier*): HtmlTag = lcButton(app.hint(nar, true), class_button)(ts: _*)

    def lcButton(action: => Unit, m: Modifier*): HtmlTag = button(class_button, onLeftClick(action))(m: _*)


  }
}
