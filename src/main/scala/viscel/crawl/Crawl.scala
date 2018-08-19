package viscel.crawl

import java.time.Instant

import org.jsoup.Jsoup
import viscel.narration.Narrator
import viscel.narration.interpretation.NarrationInterpretation
import viscel.scribe.{BlobData, Book, ImageRef, Link, PageData, Scribe, Vurl, WebContent}
import viscel.shared.{Blob, Log}
import viscel.store.BlobStore

import scala.concurrent.{ExecutionContext, Future}


class Crawl(narrator: Narrator,
            scribe: Scribe,
            blobStore: BlobStore,
            requestUtil: WebRequestInterface)
           (implicit ec: ExecutionContext) {

  val book: Book = scribe.findOrCreate(narrator)

  def start(): Future[Unit] = {
    val entry = book.beginning
    if (entry.isEmpty || entry.get.contents != narrator.archive) {
      scribe
      .addRowTo(book, PageData(Vurl.entrypoint, Vurl.entrypoint, date = Instant.now(), contents = narrator.archive))
    }
    val decider = Decider(
      images = book.emptyImageRefs(),
      links = book.volatileAndEmptyLinks(),
      book = book)

    interpret(decider)
  }

  def interpret(decider: Decider): Future[Unit] = {
    val (decision, nextDecider) = decider.tryNextImage()
    decision match {
      case ImageD(image) => handleImage(image).flatMap(_ => interpret(nextDecider))
      case LinkD(link) => handleLink(link, nextDecider).flatMap(interpret)
      case Done => Future.successful(())
    }
  }

  private def handleImage(image: ImageRef): Future[Unit] =
    requestUtil.requestBlob(image.ref, Some(image.origin)).map { response =>

      val sha1 = blobStore.write(response.content)
      val blob = BlobData(image.ref, response.location,
                          blob = Blob(
                            sha1 = sha1,
                            mime = response.mime),
                          date = response.lastModified.getOrElse(Instant.now()))

      scribe.addRowTo(book, blob)
    }


  private def handleLink(link: Link, decider: Decider) =
    requestUtil.request(link.ref) map { response =>

      val doc = Jsoup.parse(response.content, response.location.uriString())

      val contents = NarrationInterpretation
                     .interpret[List[WebContent]](narrator.wrapper, doc, link)
                     .fold(identity, r => throw WrappingException(link, r))


      Log.Clockwork.trace(s"reqest page $link, yielding $contents")
      val page = PageData(link.ref,
                          Vurl.fromString(doc.location()),
                          contents = contents,
                          date = response.lastModified.getOrElse(Instant.now()))

      scribe.addRowTo(book, page)
      decider.addContents(contents)
    }


}
