package viscel.crawl

case class Decider(images: List[VRequest.Blob],
                   links: List[VRequest.Text],
                   recheck: List[VRequest.Text] = Nil,
                   requestAfterRecheck: Int = 0,
                   imageDecisions: Int = 0,
                   rechecksDone: Int = 0,
                   recheckStarted: Boolean = false,
                  ) {

  type Decision = Option[VRequest]
  def Done: Option[Nothing] = None

  /** Adds the contents to the current decision pool.
    * Does some recheck logic, see [[rightmostRecheck]].
    * Adds everything in a left to right order, so downloads happen as users would read.
    * Does filter already contained content. */
  def addContents(contents: List[VRequest]): Decider = {
    val nextDecider = if (recheckStarted) {
      copy(requestAfterRecheck = requestAfterRecheck + (if (contents.isEmpty && requestAfterRecheck == 0) 2 else 1))
    } else this

    contents.reverse.foldLeft(nextDecider) {
      case (dec, link@VRequest.Text(_, _)) => dec.copy(links = link :: links)
      case (dec, art@VRequest.Blob(_, _)) => dec.copy(images = art :: images)
      case (dec, _) => dec
    }
  }


  def tryNextImage(): (Decision, Decider) = {
    images match {
      case h :: t =>
        (Some(h), copy(images = t, imageDecisions = imageDecisions + 1))
      case Nil =>
        tryNextLink()
    }
  }

  def tryNextLink(): (Decision, Decider) = {
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
  def rightmostRecheck(): (Decision, Decider) = {

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
