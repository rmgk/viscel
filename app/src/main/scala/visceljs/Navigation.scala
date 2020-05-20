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
