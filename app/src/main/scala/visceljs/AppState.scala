package visceljs

import scala.scalajs.js.URIUtils.encodeURIComponent
import scala.scalajs.js.URIUtils.decodeURIComponent
import viscel.shared.Vid
import visceljs.AppState.ViewState

sealed abstract class AppState(val urlhash: String) {
  def transformPos(f: Int => Int) = this match {
    case ViewState(id, pos) => ViewState(id, f(pos))
    case other => other
  }
  def position: Int = this match {
    case ViewState(id, pos) => pos
    case _ => 0
  }
}
object AppState {
  def parse(path: String): AppState = {
    val paths = path.substring(1).split("/").toList
    paths match {
      case Nil | "" :: Nil          => IndexState
      case encodedId :: Nil         =>
        val id = Vid.from(decodeURIComponent(encodedId))
        FrontState(id)
      case encodedId :: posS :: Nil =>
        val id  = Vid.from(decodeURIComponent(encodedId))
        val pos = Integer.parseInt(posS)
        ViewState(id, pos - 1)
      case _                        => IndexState
    }
  }

  case object IndexState extends AppState("")
  case class FrontState(id: Vid) extends AppState(encodeURIComponent(id.str))
  case class ViewState(id: Vid, pos: Int) extends AppState(s"${encodeURIComponent(id.str)}/${pos + 1}")
}
