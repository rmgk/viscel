package viscel.crawl

import java.time.Instant

import viscel.narration.Narrator
import viscel.scribe.{Book, ImageRef, Link, PageData, Scribe, Vurl, WebContent}

import scala.concurrent.{ExecutionContext, Future}

sealed trait Decision
case class ImageD(img: ImageRef) extends Decision
case class LinkD(link: Link) extends Decision
case object Done extends Decision


class Crawl(narrator: Narrator,
            book: Book,
            scribe: Scribe,
            requestUtil: RequestUtil)
           (implicit ec: ExecutionContext) {


  def start(): Future[Unit] = {
    val entry = book.beginning
    if (entry.isEmpty || entry.get.contents != narrator.archive) {
      scribe.addRowTo(book, PageData(Vurl.entrypoint, Vurl.entrypoint, date = Instant.now(), contents = narrator.archive))
    }
    val decider = new Decider(
      images = book.emptyImageRefs(),
      links = book.volatileAndEmptyLinks(),
      book = book)

    interpret(decider)
  }

  def interpret(decider: Decider): Future[Unit] = {
    val decision = decider.tryNextImage()
    decision match {
      case ImageD(image) => handleImage(image).flatMap(_ => interpret(decider))
      case LinkD(link) => handleLink(link, decider).flatMap(_ => interpret(decider))
      case Done => Future.successful(())
    }
  }

  private def handleImage(image: ImageRef): Future[Unit] =
    requestUtil.requestBlob(image.ref, Some(image.origin)).map(scribe.addRowTo(book, _))

  private def handleLink(link: Link, decider: Decider) =
    requestUtil.requestPage(link, narrator) map { page =>
      decider.addContents(page.contents)
      scribe.addRowTo(book, page)
    }


}


class Decider(var images: List[ImageRef], var links: List[Link], book: Book) {

  var imageDecisions = 0
  var rechecksDone = 0
  var recheckStarted = false
  var requestAfterRecheck = 0
  var recheck: List[Link] = _


  /** Adds the contents to the current decision pool.
    * Does some recheck logic, see [[rightmostRecheck]].
    * Adds everything in a left to right order, so downlods happen as users would read.
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
