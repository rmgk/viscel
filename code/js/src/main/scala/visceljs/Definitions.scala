package visceljs

import org.scalajs.dom.MouseEvent
import scalatags.JsDom.all.{
  Frag, HtmlTag, Modifier, SeqFrag, Tag, a, bindJsAnyLike, button, cls, href, onclick, raw, stringAttr
}
import viscel.shared.{Blob, Vid}

import scala.scalajs.js.URIUtils.encodeURIComponent

object Definitions {

  def path_main                      = "#"
  def path_asset(vid: Vid, pos: Int) = s"#${encodeURIComponent(vid.str)}/${pos + 1}"
  def path_blob(blob: Blob)          = s"blob/${blob.sha1}?mime=${blob.mime}"
  def path_front(vid: Vid)           = s"#${encodeURIComponent(vid.str)}"
  def path_tools                     = "tools"

  val class_placeholder = cls := "placeholder"
  val class_preview     = cls := "preview"

  def link_tools(ts: Frag*): Tag = a(href := path_tools)(ts)

  def getDefined[T](ts: T*): Option[T] = ts.find(v => v != null && !scalajs.js.isUndefined(v))
  private val dDocument                = scala.scalajs.js.Dynamic.global.document

  def isFullscreen(): Boolean =
    getDefined(
      dDocument.fullscreenElement,
      dDocument.webkitFullscreenElement,
      dDocument.mozFullScreenElement,
      dDocument.msFullscreenElement
    ).isDefined

  def toggleFullscreen(): Unit = {
    val de = dDocument.documentElement

    def requestFullscreen =
      getDefined(
        de.requestFullscreen,
        de.msRequestFullscreen,
        de.mozRequestFullScreen,
        de.webkitRequestFullscreen
      )

    def exitFullscreen =
      getDefined(
        dDocument.exitFullscreen,
        dDocument.webkitExitFullscreen,
        dDocument.mozCancelFullScreen,
        dDocument.msExitFullscreen
      )

    if (isFullscreen()) exitFullscreen.foreach(_.call(dDocument)) else requestFullscreen.foreach(_.call(de))
  }

  def lcButton(action: => Unit, m: Modifier*): HtmlTag =
    button(onclick := { (e: MouseEvent) =>
      if (e.button == 0) {
        e.preventDefault()
        action
      }
    })(m: _*)

}

object Icons {

  // icons MIT licensed: https://feathericons.com/

  val prev: Modifier = raw(
    """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"></polyline></svg>"""
  )
  val next: Modifier = raw(
    """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"></polyline></svg>"""
  )

  val modus: Modifier = raw(
    """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="4" y1="21" x2="4" y2="14"></line><line x1="4" y1="10" x2="4" y2="3"></line><line x1="12" y1="21" x2="12" y2="12"></line><line x1="12" y1="8" x2="12" y2="3"></line><line x1="20" y1="21" x2="20" y2="16"></line><line x1="20" y1="12" x2="20" y2="3"></line><line x1="1" y1="14" x2="7" y2="14"></line><line x1="9" y1="8" x2="15" y2="8"></line><line x1="17" y1="16" x2="23" y2="16"></line></svg>"""
  )
  val bookmark: Modifier = raw(
    """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"></path></svg>"""
  )
  val maximize: Modifier = raw(
    """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 3 21 3 21 9"></polyline><polyline points="9 21 3 21 3 15"></polyline><line x1="21" y1="3" x2="14" y2="10"></line><line x1="3" y1="21" x2="10" y2="14"></line></svg>"""
  )
  val externalLink: Modifier = raw(
    """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"></path><polyline points="15 3 21 3 21 9"></polyline><line x1="10" y1="14" x2="21" y2="3"></line></svg>"""
  )
  val front: Modifier = raw(
    """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="8" y1="6" x2="21" y2="6"></line><line x1="8" y1="12" x2="21" y2="12"></line><line x1="8" y1="18" x2="21" y2="18"></line><line x1="3" y1="6" x2="3.01" y2="6"></line><line x1="3" y1="12" x2="3.01" y2="12"></line><line x1="3" y1="18" x2="3.01" y2="18"></line></svg>"""
  )

  val archive: Modifier = raw(
    """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="21 8 21 21 3 21 3 8"></polyline><rect x="1" y="3" width="22" height="5"></rect><line x1="10" y1="12" x2="14" y2="12"></line></svg>"""
  )

}
