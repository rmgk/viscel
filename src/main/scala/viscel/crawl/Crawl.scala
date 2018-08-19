package viscel.crawl

import java.time.Instant

import org.jsoup.Jsoup
import viscel.narration.Narrator
import viscel.narration.interpretation.NarrationInterpretation
import viscel.scribe.{BlobData, ImageRef, Link, PageData, Scribe, Vurl, WebContent}
import viscel.shared.{Blob, Log}
import viscel.store.BlobStore

import scala.concurrent.{ExecutionContext, Future}

class ScribeNarratorAdapter(scribe: Scribe, narrator: Narrator, blobStore: BlobStore) {
  val book = scribe.findOrCreate(narrator)

  private def vreqFromImageRef(ir: ImageRef): VRequest.Blob = VRequest.Blob(ir.ref, Some(ir.origin))(storeImage)
  private def vreqFromLink(link: Link): VRequest.Text = VRequest.Text(link.ref, None)(storePage(link))

  def missingBlobs(): List[VRequest.Blob] = book.emptyImageRefs().map(vreqFromImageRef)
  def linksToCheck(): List[VRequest.Text] = book.volatileAndEmptyLinks().map(vreqFromLink)
  def rechecks(): List[VRequest.Text] = book.computeRightmostLinks().map(vreqFromLink)

  def init(): Unit = {
    val entry = book.beginning
    if (entry.isEmpty || entry.get.contents != narrator.archive) {
      scribe.addRowTo(book,
                      PageData(Vurl.entrypoint, Vurl.entrypoint, date = Instant.now(), contents = narrator.archive))
    }
  }

  def storeImage(response: VResponse[Array[Byte]]): List[VRequest] = {

    val sha1 = blobStore.write(response.content)
    val blob = BlobData(response.request.href, response.location,
                        blob = Blob(
                          sha1 = sha1,
                          mime = response.mime),
                        date = response.lastModified.getOrElse(Instant.now()))

    scribe.addRowTo(book, blob)
    Nil
  }


  def storePage(link: Link)(response: VResponse[String]): List[VRequest] = {

    val doc = Jsoup.parse(response.content, response.location.uriString())

    val contents = NarrationInterpretation
                   .interpret[List[WebContent]](narrator.wrapper, doc, link)
                   .fold(identity, r => throw WrappingException(link, r))


    Log.Clockwork.trace(s"request page ${response.location}, yielding $contents")
    val page = PageData(response.request.href,
                        Vurl.fromString(doc.location()),
                        contents = contents,
                        date = response.lastModified.getOrElse(Instant.now()))

    scribe.addRowTo(book, page)
    contents.collect {
      case ir@ImageRef(_, _, _) if !book.hasBlob(ir.ref) => vreqFromImageRef(ir)
      case l@Link(_, _, _) if !book.hasPage(l.ref) => vreqFromLink(l)
    }
  }


}

class Crawl(graph: ScribeNarratorAdapter,
            requestUtil: WebRequestInterface)
           (implicit ec: ExecutionContext) {

  def start(): Future[Unit] = {
    graph.init()

    val decider = Decider(
      images = graph.missingBlobs(),
      links = graph.linksToCheck(),
      recheck = graph.rechecks()
    )

    interpret(decider)
  }

  def interpret(decider: Decider): Future[Unit] = {
    val (decision, nextDecider) = decider.tryNextImage()
    decision match {
      case Some(image@VRequest.Blob(_, _)) =>
        requestUtil.request(image).flatMap { response =>
          val requests = image.handler(response)
          interpret(nextDecider.addContents(requests))
        }
      case Some(link@VRequest.Text(_, _)) =>
        requestUtil.request(link).flatMap { response =>
          val requests = link.handler(response)
          interpret(nextDecider.addContents(requests))
        }
      case None => Future.successful(())
    }
  }

}
