package visceljs.render

import org.scalajs.dom
import org.scalajs.dom.html.Element
import rescala.default._
import scalatags.JsDom.TypedTag
import scalatags.JsDom.all.{alt, stringFrag, _}
import scalatags.JsDom.tags2.{nav, section}
import viscel.shared.{Blob, SharedImage}
import visceljs.Definitions._
import visceljs.{Definitions, Icons, MetaInfo}

sealed trait FitType {
  def next: FitType =
    this match {
      case FitType.W   => FitType.WH
      case FitType.WH  => FitType.O
      case FitType.O   => FitType.SW
      case FitType.SW  => FitType.SWH
      case FitType.SWH => FitType.W
    }
}
object FitType {
  case object W   extends FitType
  case object WH  extends FitType
  case object O   extends FitType
  case object SW  extends FitType
  case object SWH extends FitType
}

object Snippets {

  def imageStyle(fitType: FitType): String = {
    (fitType match {
      case FitType.O   => ""
      case FitType.W   => "max-width: 100%"
      case FitType.WH  => "max-height: 100vh; max-width: 100%; width: auto"
      case FitType.SWH => "height: 100vh; width: 100%; object-fit: contain"
      case FitType.SW  => "width: 100%"
    })
  }

  def asset(asset: SharedImage, addImageStyle: Modifier = ""): Tag = {
    asset.blob match {
      case blob @ Blob(_, "application/x-shockwave-flash") =>
        `object`(
          `type` := "application/x-shockwave-flash",
          data := path_blob(blob),
          width := asset.data.getOrElse("width", ""),
          height := asset.data.getOrElse("height", "")
        )
      case blob @ Blob(_, _) =>
        img(src := path_blob(blob), title := asset.data.getOrElse("title", ""), alt := asset.data.getOrElse("alt", ""))(
          addImageStyle
        )
    }
  }

  def fullscreenToggle(stuff: Modifier*): HtmlTag = lcButton(Definitions.toggleFullscreen())(stuff: _*)

  def group(name: String, entries: Seq[FrontPageEntry]): TypedTag[dom.Element] = {
    def sumOf(fun: FrontPageEntry => Int) = entries.iterator.map(fun).sum
    val totalNewPages                     = sumOf(e => math.max(0, e.newPages))
    val totalPages                        = sumOf(_.size)
    val bookmarkedPages                   = sumOf(_.bookmarkPosition)
    val elements = entries.map { fpe =>
      val newPages = fpe.newPages
      li(a(
        href := path_front(fpe.id),
        s"${fpe.name}${if (newPages == 0) "" else s" ($newPages)"}", {
          if (!fpe.linked) span(" ", Icons.unavailable, cls := "unlinked", title := "not linked to live sources")
          else frag()
        }
      ))
    }

    section(
      h1(s"$name $totalNewPages ($bookmarkedPages/$totalPages)"),
      ul(elements),
      if (elements.isEmpty) cls := "empty" else ""
    )
  }

  def navigation(links: Modifier*): HtmlTag =
    nav(links: _*)

  def meta(meta: MetaInfo): Signal[TypedTag[Element]] = {
    val connectionStatus = meta.connection.map {
      case 0     => stringFrag(s"disconnected (attempt № ${meta.reconnecting.value})")
      case other => stringFrag(s"$other active")
    }
    Signal {
      section(List[Frag](
        s"app version: ${meta.version}",
        br(),
        s"server version: ",
        meta.remoteVersion.value,
        br(),
        s"service worker: ",
        meta.serviceState.value,
        br(),
        s"connection status: ",
        connectionStatus.value,
        br()
      ))
    }
  }

}
