package viscel.crawl

import viscel.narration.Templates
import viscel.netzi.VRequest


case class Decider(links: List[VRequest] = Nil,
                   recheck: List[VRequest] = Nil,
                   requestAfterRecheck: Int = 0,
                   decisions: Int = 0,
                   rechecksDone: Int = 0,
                   recheckStarted: Boolean = false,
                  ) {

  type Decision = Option[(VRequest, Decider)]

  /** Adds the contents to the current decision pool.
    * Does some recheck logic, see [[rightmostRecheck]].
    * Adds everything in a left to right order, so downloads happen as users would read. */
  def addTasks(toAdd: List[VRequest]): Decider = {
    val nextDecider = if (recheckStarted) {
      copy(requestAfterRecheck = requestAfterRecheck + (if (toAdd.isEmpty && requestAfterRecheck == 0) 2 else 1))
    } else this

    nextDecider.copy(links = toAdd ::: nextDecider.links)

  }

  def decide(): Decision = {
    links match {
      case link :: t =>
        val newDecision = if (link.context.contains(Templates.Volatile)) 0 else 1
        Some((link, copy(links = t, decisions = decisions + newDecision)))
      case Nil =>
        rightmostRecheck()
    }
  }

  /** Handles rechecking logic.
    * On first call:
    *   - Done immediately, if we already made an ImageD (no rechecks after downloads)
    *   - Initializes the path to the rightmost child elements starting from the root.
    * Checks one or two layers deep, depending on weather we find anything in the last layer.
    * If we find nothing, then we check no further (there was something there before, why is it gone?) */
  private def rightmostRecheck(): Decision = {

    if (!recheckStarted && (decisions > 0) ) return None

    if (rechecksDone == 0 || (rechecksDone == 1 && requestAfterRecheck > 1)) {
      recheck match {
        case Nil => None
        case link :: tail =>
          Some((link, copy(recheckStarted = true, recheck = tail, rechecksDone = rechecksDone + 1)))
      }
    }
    else {
      None
    }
  }
}
