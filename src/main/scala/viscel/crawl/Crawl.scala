package viscel.crawl

import viscel.scribe.{Link, ScribeNarratorAdapter}

import scala.concurrent.{ExecutionContext, Future}


sealed trait CrawlTask
object CrawlTask {
  case class Image(req: VRequest) extends CrawlTask
  case class Page(req: VRequest, from: Link) extends CrawlTask
}


class Crawl(graph: ScribeNarratorAdapter,
            requestUtil: WebRequestInterface)
           (implicit ec: ExecutionContext) {

  def start(): Future[Unit] = {
    graph.init()

    val decider = Decider(recheck = graph.rechecks()).addTasks(graph.initialTasks())

    interpret(decider)
  }

  def interpret(decider: Decider): Future[Unit] = {
    val (decision, nextDecider) = decider.decide()
    decision match {

      case Some(CrawlTask.Image(imageRequest)) =>
        requestUtil.getBytes(imageRequest).flatMap { response =>
          graph.storeImage(response)
          interpret(nextDecider)
        }
      case Some(CrawlTask.Page(request, from)) =>
        requestUtil.getString(request).flatMap { response =>
          val requests = graph.storePage(from)(response)
          interpret(nextDecider.addTasks(requests))
        }
      case None => Future.successful(())

    }
  }

}
