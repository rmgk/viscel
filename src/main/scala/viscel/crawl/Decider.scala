package viscel.crawl


case class Decider(images: List[CrawlTask] = Nil,
                   links: List[CrawlTask] = Nil,
                   recheck: List[CrawlTask] = Nil,
                   requestAfterRecheck: Int = 0,
                   imageDecisions: Int = 0,
                   rechecksDone: Int = 0,
                   recheckStarted: Boolean = false,
                  ) {

  import scala.{None => Done}
  type Decision = Option[CrawlTask]

  /** Adds the contents to the current decision pool.
    * Does some recheck logic, see [[rightmostRecheck]].
    * Adds everything in a left to right order, so downloads happen as users would read. */
  def addTasks(toAdd: List[CrawlTask]): Decider = {
    val nextDecider = if (recheckStarted) {
      copy(requestAfterRecheck = requestAfterRecheck + (if (toAdd.isEmpty && requestAfterRecheck == 0) 2 else 1))
    } else this

    toAdd.reverse.foldLeft(nextDecider) {
      case (dec, ct@CrawlTask.Page(_, _)) => dec.copy(links = ct :: dec.links)
      case (dec, art@CrawlTask.Image(_)) => dec.copy(images = art :: dec.images)
      case (dec, _) => dec
    }
  }

  def decide(): (Decision, Decider) = tryNextImage()

  private def tryNextImage(): (Decision, Decider) = {
    images match {
      case h :: t =>
        (Some(h), copy(images = t, imageDecisions = imageDecisions + 1))
      case Nil =>
        tryNextLink()
    }
  }

  private def tryNextLink(): (Decision, Decider) = {
    links match {
      case link :: t =>
        (Some(link), copy(links = t))
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
  private def rightmostRecheck(): (Decision, Decider) = {

    if (!recheckStarted && (imageDecisions > 0) ) return (Done, this)

    if (rechecksDone == 0 || (rechecksDone == 1 && requestAfterRecheck > 1)) {
      recheck match {
        case Nil => (Done, copy(recheckStarted = true, rechecksDone = rechecksDone + 1))
        case link :: tail =>
          (Some(link), copy(recheckStarted = true, recheck = tail, rechecksDone = rechecksDone + 1))
      }
    }
    else {
      (Done, this)
    }
  }
}
