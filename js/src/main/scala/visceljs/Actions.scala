package visceljs

import rescala.default.*
import scalatags.JsDom.all.{HtmlTag, Modifier, button, disabled}
import viscel.shared.{Bookmark, SharedImage, Vid}
import visceljs.Definitions.lcButton
import visceljs.connection.{BookmarkManager, ContentConnectionManager}

class Actions(ccm: ContentConnectionManager, bookmarkManager: BookmarkManager) {

  def postBookmark(vid: Vid, bm: Int, current: Int, cdat: Option[SharedImage], ts: Modifier*): HtmlTag = {
    if (current != bm) {
      lcButton {
        val bookmark = Bookmark(bm, System.currentTimeMillis(), cdat.map(_.blob.sha1), cdat.map(_.origin))
        bookmarkManager.setBookmark.fire(vid -> bookmark)
      }(ts*)
    } else {
      button(disabled)(ts*)
    }
  }

  def postForceHint(vid: Vid, ts: Modifier*): HtmlTag = lcButton(ccm.hint(vid, force = true))(ts*)

}
