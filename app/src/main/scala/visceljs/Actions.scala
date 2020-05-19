package visceljs

import scalatags.JsDom.all.{HtmlTag, Modifier, button, disabled}
import viscel.shared.{Bookmark, Vid}
import visceljs.Definitions.lcButton

class Actions(hint: (Vid, Boolean) => Unit,
              postBookmarkF: (Vid, Bookmark) => Unit) {


  def postBookmark(bm: Int, data: Data, ts: Modifier*): HtmlTag = {
    if (data.bookmark != bm) {
      lcButton {
        val cdat     = data.content.gallery.atPos(bm).get
        val bookmark = Bookmark(bm, System.currentTimeMillis(), cdat.map(_.blob.sha1), cdat.map(_.origin))
        postBookmarkF(data.id, bookmark)
      }(ts: _*)
    }
    else {
      button(disabled)(ts: _*)
    }
  }

  def postForceHint(vid: Vid, ts: Modifier*): HtmlTag = lcButton(hint(vid, true))(ts: _*)

}
