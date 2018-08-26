package visceljs

import viscel.shared.Vid

sealed trait AppState
object AppState {
  case object IndexState extends AppState
  case class FrontState(id: Vid) extends AppState
  case class ViewState(id: Vid, pos: Int) extends AppState
}
