package visceljs

import scalatags.JsDom.all.{HtmlTag, Modifier, button, disabled}
import viscel.shared.{Bookmark, Vid}
import visceljs.Definitions.lcButton
import visceljs.connection.{BookmarkManager, ContentConnectionManager}
import rescala.default._

class Actions(ccm: ContentConnectionManager,
              bookmarkManager: BookmarkManager) {


  def postBookmark(bm: Int, data: Data, ts: Modifier*): HtmlTag = {
    if (data.bookmark != bm) {
      lcButton {
        val cdat     = data.content.gallery.atPos(bm).get
        val bookmark = Bookmark(bm, System.currentTimeMillis(), cdat.map(_.blob.sha1), cdat.map(_.origin))
        bookmarkManager.setBookmark.fire(data.id -> bookmark)
      }(ts: _*)
    }
    else {
      button(disabled)(ts: _*)
    }
  }

  def postForceHint(vid: Vid, ts: Modifier*): HtmlTag = lcButton(ccm.hint(vid, force = true))(ts: _*)

}
