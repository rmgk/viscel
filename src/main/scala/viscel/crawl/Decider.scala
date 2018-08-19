package viscel.crawl

import viscel.scribe.{Book, ImageRef, Link, WebContent}

class Decider(var images: List[ImageRef], var links: List[Link], book: Book) {

  var imageDecisions = 0
  var rechecksDone = 0
  var recheckStarted = false
  var requestAfterRecheck = 0
  var recheck: List[Link] = _


  /** Adds the contents to the current decision pool.
    * Does some recheck logic, see [[rightmostRecheck]].
    * Adds everything in a left to right order, so downloads happen as users would read.
    * Does filter already contained content. */
  def addContents(contents: List[WebContent]): Unit = {
    if (recheckStarted) {
      if (contents.isEmpty && requestAfterRecheck == 0) requestAfterRecheck += 1
      requestAfterRecheck += 1
    }

    contents.reverse.foreach {
      case link@Link(ref, _, _) if !book.hasPage(ref) => links = link :: links
      case art@ImageRef(ref, _, _) if !book.hasBlob(ref) => images = art :: images
      case _ =>
    }
  }


  def tryNextImage(): Decision = {
    images match {
      case h :: t =>
        images = t
        imageDecisions += 1
        ImageD(h)
      case Nil =>
        tryNextLink()
    }
  }

  def tryNextLink(): Decision = {
    links match {
      case link :: t =>
        links = t
        LinkD(link)
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
  def rightmostRecheck(): Decision = {
    if (!recheckStarted) {
      if (imageDecisions > 0) return Done
      recheckStarted = true
      recheck = book.computeRightmostLinks()
    }

    if (rechecksDone == 0 || (rechecksDone == 1 && requestAfterRecheck > 1)) {
      rechecksDone += 1
      recheck match {
        case Nil => Done
        case link :: tail =>
          recheck = tail
          LinkD(link)
      }
    }
    else {
      Done
    }
  }
}
