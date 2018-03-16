package visceljs

import org.scalajs.dom.html.UList
import rescala.Signal
import viscel.shared.{Blob, Description, SharedImage}
import visceljs.Definitions._

import scalatags.JsDom.all.{stringFrag, alt, _}
import scalatags.JsDom.attrs.{onclick, style}
import scalatags.JsDom.tags.a
import scalatags.JsDom.tags2.{nav, section}

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
      case None => span(class_placeholder, "placeholder")
      case Some(blob@Blob(_, "application/x-shockwave-flash")) =>
        `object`(
          `type` := "application/x-shockwave-flash",
          data := path_blob(blob),
          width := asset.data.getOrElse("width", ""),
          height := asset.data.getOrElse("height", ""))
      case Some(blob@Blob(_, _)) =>
        img(src := path_blob(blob), title := asset.data.getOrElse("title", ""), alt := asset.data.getOrElse("alt", ""))(addImageStyle)
    }
  }

  def fullscreenToggle(stuff: Modifier*): HtmlTag = a(cls := "pure-button", onclick := (() => Definitions.toggleFullscreen()))(stuff: _*)


  def group(name: String, actions: Actions, entries: Signal[Seq[(Description, Int, Int)]]): Tag = {
    val elements: UList = ul.render
    val rLegend = legend(name).render
    entries.observe { es =>
      elements.innerHTML = ""
      var cUnread = 0
      var cTotal = 0
      var cPos = 0
      es.foreach { case (desc, pos, unread) =>
        val e = actions.Tags.link_front(desc, s"${desc.name}${if (unread == 0) "" else s" ($unread)"}",
          {if (desc.unknownNarrator) span(" ", Icons.archive, cls := "unlinked", title := "not linked to live sources") else frag()})
        elements.appendChild(li(e).render)
        if (unread > 0) cUnread += unread
        cTotal += desc.size
        cPos += pos
      }
      rLegend.textContent = s"$name $cUnread ($cPos/$cTotal)"
    }

    section(fieldset(rLegend, elements))
  }

  def navigation(links: Tag*): HtmlTag =
    nav(class_button_group)(links)
}
