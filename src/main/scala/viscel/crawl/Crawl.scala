package viscel.crawl

import viscel.scribe.ScribeNarratorAdapter

import scala.concurrent.{ExecutionContext, Future}

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
        requestUtil.get(image).flatMap { response =>
          val requests = image.handler(response)
          interpret(nextDecider.addContents(requests))
        }
      case Some(link@VRequest.Text(_, _)) =>
        requestUtil.get(link).flatMap { response =>
          val requests = link.handler(response)
          interpret(nextDecider.addContents(requests))
        }
      case None => Future.successful(())
    }
  }

}
