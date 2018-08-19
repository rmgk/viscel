package viscel.crawl

import viscel.scribe.{Book, ImageRef, Link, WebContent}

case class Decider(images: List[ImageRef],
                   links: List[Link],
                   book: Book,
                   imageDecisions: Int = 0,
                   rechecksDone: Int = 0,
                   recheckStarted: Boolean = false,
                   requestAfterRecheck: Int = 0,
                   recheck: List[Link] = Nil
                  ) {


  /** Adds the contents to the current decision pool.
    * Does some recheck logic, see [[rightmostRecheck]].
    * Adds everything in a left to right order, so downloads happen as users would read.
    * Does filter already contained content. */
  def addContents(contents: List[WebContent]): Decider = {
    val nextDecider = if (recheckStarted) {
      copy(requestAfterRecheck = requestAfterRecheck + (if (contents.isEmpty && requestAfterRecheck == 0) 2 else 1))
    } else this

    contents.reverse.foldLeft(nextDecider) {
      case (dec, link@Link(ref, _, _)) if !book.hasPage(ref) => dec.copy(links = link :: links)
      case (dec, art@ImageRef(ref, _, _)) if !book.hasBlob(ref) => dec.copy(images = art :: images)
      case (dec, _) => dec
    }
  }


  def tryNextImage(): (Decision, Decider) = {
    images match {
      case h :: t =>
        (ImageD(h), copy(images = t,  imageDecisions = imageDecisions + 1))
      case Nil =>
        tryNextLink()
    }
  }

  def tryNextLink(): (Decision, Decider) = {
    links match {
      case link :: t =>
        (LinkD(link), copy(links = t))
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

    val nextDecider = if (recheckStarted) this else {
      if (imageDecisions > 0) return (Done, this)
      copy(
        recheckStarted = true,
        recheck = book.computeRightmostLinks()
      )
    }

    if (rechecksDone == 0 || (rechecksDone == 1 && requestAfterRecheck > 1)) {
      nextDecider.recheck match {
        case Nil => (Done, nextDecider.copy(rechecksDone = rechecksDone + 1))
        case link :: tail =>
          (LinkD(link), nextDecider.copy(recheck = tail, rechecksDone = rechecksDone + 1))
      }
    }
    else {
      (Done, nextDecider)
    }
  }
}
