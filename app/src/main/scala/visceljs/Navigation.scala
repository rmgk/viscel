package visceljs

import org.scalajs.dom
import org.scalajs.dom.raw.KeyboardEvent
import rescala.core.{CreationTicket, REName}
import rescala.default.{Event, Evt, implicitScheduler}
import rescala.reactives.Events

object Navigation {
  sealed trait Navigate
  case object Next extends Navigate
  case object Prev extends Navigate
  case class Mode(i: Int) extends Navigate


  val handleKeypress = Events.fromCallback[KeyboardEvent](dom.document.onkeydown = _)(
    CreationTicket.fromEngineImplicit(implicitScheduler, REName.create), implicitScheduler)
  val navigate = Evt[Navigate]
  val keypressNavigations = handleKeypress.event.map(_.key).collect {
    case "ArrowLeft" | "a" | "," => Prev
    case "ArrowRight" | "d" | "." => Next
    case n if n.matches("""^\d+$""") => Mode(n.toInt)
  }

  val navigationEvents: Event[Navigate] = keypressNavigations || navigate

}
