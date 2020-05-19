package visceljs.render

import org.scalajs.dom
import rescala.default._
import rescala.extra.Tags._
import scalatags.JsDom
import scalatags.JsDom.TypedTag
import scalatags.JsDom.all.{alt, stringFrag, _}
import scalatags.JsDom.attrs.style
import scalatags.JsDom.tags2.{nav, section}
import viscel.shared.{Blob, SharedImage}
import visceljs.Definitions._
import visceljs.{Actions, Data, Definitions, Icons, MetaInfo}

sealed trait FitType {
  def next: FitType = this match {
    case FitType.W  => FitType.WH
    case FitType.WH => FitType.O
    case FitType.O  => FitType.W
  }
}
object FitType {
  import upickle.default._
  implicit val codec: ReadWriter[FitType] = macroRW
  case object W extends FitType
  case object WH extends FitType
  case object O extends FitType
}

object Snippets {


  def imageStyle(fitType: FitType): Modifier = {
    style := (fitType match {
      case FitType.O  => ""
      case FitType.W  => "max-width: 100%"
      case FitType.WH => "max-height: 100vh; max-width: 100%; width: auto"
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
      val e = actions.link_front(fpe.id,
                                 s"${desc.name}${if (unread == 0) "" else s" ($unread)"}",
        {
          if (!desc.linked) span(" ",
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

  def meta(meta: MetaInfo): JsDom.Modifier = {
    val connectionStatus = meta.connection.map{
      case 0 => stringFrag(s"disconnected (tries ${meta.reconnecting.value})")
      case other => stringFrag(s"$other active")
    }
    section(s"app version: ${meta.version}", br(),
            s"server version: ", meta.remoteVersion.map(stringFrag).asModifier, br(),
            s"service worker: ", meta.serviceState.map(stringFrag).asModifier, br(),
            s"connection status: ", connectionStatus.asModifier, br()
      //meta.registry.remotes.map(_.toList.map{ case (rr, state) =>
      //  li(s"$rr: ", state.connected.map(s => stringFrag(if (s) "connected" else "disconnected")).asModifier)
      //}).asModifierL
    )
  }

}
