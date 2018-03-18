package visceljs

sealed trait AppState
object AppState {
  case object IndexState extends AppState
  case class FrontState(id: String) extends AppState
  case class ViewState(id: String, pos: Int) extends AppState
}
