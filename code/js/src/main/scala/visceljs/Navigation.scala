package visceljs

import org.scalajs.dom
import org.scalajs.dom.KeyboardEvent
import rescala.default.{Event, Evt, implicitScheduler, Events}
import visceljs.render.FitType

object Navigation {
  sealed trait Navigate derives CanEqual
  case object Next            extends Navigate
  case object Prev            extends Navigate
  case class Mode(i: FitType) extends Navigate

  case class Position(internal: Int, max: Option[Int]) {
    def cur: Int = math.max(0, max.fold(internal)(math.min(_, internal)))
    def mov(nav: Navigate): Position =
      nav match {
        case Navigation.Next => mov(1)
        case Navigation.Prev => mov(-1)
        case _               => this
      }
    def mov(i: Int): Position   = set(cur + i)
    def set(i: Int): Position   = Position(i, max)
    def limit(m: Int): Position = copy(max = Some(m))
  }

  val navigate       = Evt[Navigate]()
  val handleKeypress = Events.fromCallback[KeyboardEvent](dom.document.onkeydown = _)
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
