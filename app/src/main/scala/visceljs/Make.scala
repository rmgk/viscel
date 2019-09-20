package visceljs

import org.scalajs.dom
import scalatags.JsDom.all.{alt, stringFrag, _}
import scalatags.JsDom.TypedTag
import scalatags.JsDom.attrs.style
import scalatags.JsDom.tags2.{nav, section}
import viscel.shared.{Blob, SharedImage}
import visceljs.Definitions._
import visceljs.render.FrontPageEntry

object Make {

  def imageStyle(fitType: Int): Modifier = {
    def s(mw: Boolean = false, mh: Boolean = false, w: Boolean = false, h: Boolean = false) =
      s"max-height: ${if (mh) "100vh" else "none"}; max-width: ${if (mw) "100vw" else "none"}; height: ${if (h) "100vh" else "auto"}; width: ${if (w) "100vw" else "auto"}"

    style := (fitType % 8 match {
      case 0 => ""
      case 1 => s()
      case 2 => s(mw = true)
      case 3 => s(mh = true)
      case 4 => s(mw = true, mh = true)
      case 5 => s(w = true)
      case 6 => s(h = true)
      case 7 => s(w = true, h = true)
    })
  }

  def asset(asset: SharedImage, assetData: Data, addImageStyle: Modifier = ""): Tag = {
    asset.blob match {
      case blob@Blob(_, "application/x-shockwave-flash") =>
        `object`(
          `type` := "application/x-shockwave-flash",
          data := path_blob(blob),
          width := asset.data.getOrElse("width", ""),
          height := asset.data.getOrElse("height", ""))
      case blob@Blob(_, _) =>
        img(src := path_blob(blob), title := asset.data.getOrElse("title", ""), alt := asset.data.getOrElse("alt", ""))(addImageStyle)
    }
  }

  def fullscreenToggle(stuff: Modifier*): HtmlTag = lcButton(Definitions.toggleFullscreen())(stuff: _*)


  def group(name: String, actions: Actions, entries: Seq[FrontPageEntry]): TypedTag[dom.Element] = {
    var cUnread = 0
    var cTotal = 0
    var cPos = 0
    val elements = entries.map { fpe =>
      val desc = fpe.description
      val unread = fpe.newPages
      val e = actions.link_front(desc,
                                 s"${desc.name}${if (unread == 0) "" else s" ($unread)"}",
        {
          if (desc.unknownNarrator) span(" ",
                                         Icons.archive,
                                         cls := "unlinked",
                                         title := "not linked to live sources")
          else frag()
        })
      if (unread > 0) cUnread += unread
      cTotal += desc.size
      cPos += fpe.bookmarkPosition
      li(e)
    }

    section(h1(s"$name $cUnread ($cPos/$cTotal)"), ul(elements),
            if (elements.isEmpty) cls := "empty" else "")
  }

  def navigation(links: Modifier*): HtmlTag =
    nav(links :_*)
}
