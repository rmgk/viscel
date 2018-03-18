package visceljs

import org.scalajs.dom.MouseEvent
import rescala._
import viscel.shared.{Bindings, Description, Log}
import visceljs.AppState.{FrontState, IndexState, ViewState}
import visceljs.Definitions.{class_button, class_button_disabled, path_asset, path_front}

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scalatags.JsDom.all.{HtmlTag, Modifier, Tag, a, bindJsAnyLike, button, href, onclick}

class Actions(hint: (Description, Boolean) => Unit,
              postBookmarkF: Bindings.SetBookmark => Future[Bindings.Bookmarks],
              manualStates: Evt[AppState]) {

  private def onLeftClick(a: => Unit): Modifier = onclick := { (e: MouseEvent) =>
    if (e.button == 0) {
      e.preventDefault()
      a
    }
  }

  private def gotoIndex(): Unit = manualStates.fire(IndexState)
  def gotoFront(description: Description): Unit = manualStates.fire(FrontState(description.id))
  private def gotoView(data: Data): Unit = manualStates.fire(ViewState(data.description.id, data.pos))


  private def postBookmark(nar: Description, pos: Int): Unit = {
    postBookmarkF(Some((nar, pos))).failed.foreach(e => Log.Web.error(s"posting bookmarks failed: $e"))
  }


  def button_index(ts: Modifier*): Tag = lcButton(gotoIndex(), ts: _*)
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
        postBookmark(data.description, bm)
        handler(data.copy(bookmark = bm))
      }(ts: _*)
    }
    else {
      button(class_button_disabled)(ts: _*)
    }
  }

  def postForceHint(nar: Description, ts: Modifier*): HtmlTag = lcButton(hint(nar, true), class_button)(ts: _*)

  def lcButton(action: => Unit, m: Modifier*): HtmlTag = button(class_button, onLeftClick(action))(m: _*)
}
