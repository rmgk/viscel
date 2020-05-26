package visceljs

import org.scalajs.dom
import org.scalajs.dom.raw.KeyboardEvent
import rescala.default.{Event, Evt, implicitScheduler}
import rescala.reactives.Events
import visceljs.render.FitType

object Navigation {
  sealed trait Navigate
  case object Next extends Navigate
  case object Prev extends Navigate
  case class Mode(i: FitType) extends Navigate

  case class Position(cur: Int, max: Int) {
    def mov(nav: Navigate): Position = nav match {
      case Navigation.Next => Position(math.min(cur + 1, max), max)
      case Navigation.Prev => Position(math.max(cur - 1, 0), max)
      case _         => this
    }
    def mov(i: Int): Position = set(cur + i)
    def set(i: Int): Position = Position(math.max(0, math.min(i, max)), max)
    def limit(m: Int): Position = copy(max = m)
  }

  val navigate            = Evt[Navigate]
  val handleKeypress      = Events.fromCallback[KeyboardEvent](dom.document.onkeydown = _)
  val keypressNavigations = handleKeypress.event.map(_.key).collect {
    case "ArrowLeft"  => Prev
    case "ArrowRight" => Next
    case "1"          => Mode(FitType.W)
    case "2"          => Mode(FitType.WH)
    case "3"          => Mode(FitType.O)
    case "4"          => Mode(FitType.SW)
    case "5"          => Mode(FitType.SWH)
    case "0"          => Mode(FitType.O)
  }

  val navigationEvents: Event[Navigate] = keypressNavigations || navigate

}
