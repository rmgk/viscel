package visceljs

import scalatags.JsDom.all.{HtmlTag, Modifier, Tag, a, bindJsAnyLike, button, disabled, href}
import viscel.shared.{Bookmark, Vid}
import visceljs.AppState.{FrontState, IndexState, ViewState}
import visceljs.Definitions.{lcButton, onLeftClick, path_asset, path_front, path_main}

class Actions(hint: (Vid, Boolean) => Unit,
              postBookmarkF: (Vid, Bookmark) => Unit,
              manualStates: AppState => Unit) {



  private def gotoIndex(): Unit = manualStates(IndexState)
  def gotoFront(vid: Vid): Unit = manualStates(FrontState(vid))
  private def gotoView(data: Data): Unit = manualStates(ViewState(data.id, data.pos))


  private def postBookmark(vid: Vid, pos: Int): Unit = {
    postBookmarkF(vid, Bookmark(pos, System.currentTimeMillis()))
  }


  def link_index(ts: Modifier*): Tag = a(onLeftClick(gotoIndex()), href := path_main)(ts: _*)
  def link_asset(data: Data): Tag = a.apply(onLeftClick(gotoView(data)), href := path_asset(data))
  def button_asset(data: Data): Tag = button_asset(data, gotoView(data))
  def button_asset(data: Data, onleft: => Unit): Tag = {
    if (data.gallery.isEnd) button(disabled)
    else lcButton(onleft)
  }

  def link_front(vid: Vid, ts: Modifier*): Tag =
    a(onLeftClick(gotoFront(vid)), href := path_front(vid))(ts: _*)

  def postBookmark(bm: Int, data: Data, handler: Data => Unit, ts: Modifier*): HtmlTag = {
    if (data.bookmark != bm) {
      lcButton {
        postBookmark(data.id, bm)
        handler(data.copy(bookmark = bm))
      }(ts: _*)
    }
    else {
      button(disabled)(ts: _*)
    }
  }

  def postForceHint(vid: Vid, ts: Modifier*): HtmlTag = lcButton(hint(vid, true))(ts: _*)

}
