package viscel.crawler

import java.nio.file.{Files, Paths}

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.jsoup.nodes.Document
import rescala.events.{Event, ImperativeEvent}
import rescala.propagation.MaybeTurn
import spray.client.pipelining._
import viscel.cores.Core
import viscel.store._

import scala.annotation.tailrec
import scala.collection.concurrent
import scala.concurrent.{ExecutionContext, Future}


object Clockwork extends StrictLogging {

  val shallow = false

  sealed trait Result {def job: Job }

  final case class Start(job: Job, collection: CollectionNode) extends Result

  final case class Page(job: Job, page: PageNode, document: Document) extends Result

  final case class Asset(job: Job, asset: AssetNode, blob: Network.Blob) extends Result

  final case class Done(job: Job) extends Result

  final case class Failed(job: Job, cause: Throwable) extends Result


  def selectNext(from: ArchiveNode, neo: Neo): Option[ArchiveNode] = neo.txs {
    from.findForward {
      case PageNode(page) if page.describes.isEmpty => page
      case AssetNode(asset) if (!shallow) && asset.blob.isEmpty => asset
    }
  }


  def writeAsset(core: Core, assetNode: AssetNode, blob: Network.Blob, neo: Neo) = {
    logger.debug(s"$core: received blob, applying to $assetNode")
    val path = Paths.get(viscel.hashToFilename(blob.sha1))
    Files.createDirectories(path.getParent())
    Files.write(path, blob.buffer)
    neo.txs { assetNode.blob = BlobNode.create(blob.sha1, blob.mediatype, assetNode.source) }
  }

  def writePage(core: Core, pageNode: PageNode, doc: Document, neo: Neo) = {
    logger.debug(s"$core: received ${ doc.baseUri() }, applying to $pageNode")
    ArchiveManipulation.applyDescription(pageNode, core.wrap(doc, pageNode.description))
  }

  def run() = {

  }

  @tailrec
  def next(current: ArchiveNode, job: Job, neo: Neo, ec: ExecutionContext): SendReceive => Future[Result] =
    selectNext(current, neo) match {
      case None => _ => Future.successful(Done(job))
      case Some(node) =>
        logger.debug(s"${ job.core }: selected next $node")
        node match {
          case PageNode(page) =>
            Network.getDocument(page.location).andThen(_.map { doc => Page(job, page, doc) }(ec))

          case AssetNode(asset) =>
            logger.debug(s"${ job.core }: found placeholder element, downloading")
            BlobNode.find(asset.source) match {
              case Some(blob) => asset.blob = blob; next(asset, job, neo, ec)
              case None => Network.getBlob(asset.description).andThen(_.map { blob => Asset(job, asset, blob) }(ec))
            }
        }
    }

  class Job(val core: Core, val results: ImperativeEvent[Result]) {
    override def toString: String = core.toString
  }

  val jobs: concurrent.Map[Core, Job] = concurrent.TrieMap[Core, Job]()

  val archiveHint = new ImperativeEvent[ArchiveNode]()
  val collectionHint = new ImperativeEvent[CollectionNode]()

  val hints: Event[CollectionNode] = archiveHint.map((_: ArchiveNode).collection) || collectionHint

  def pipeBack(in: Event[Result])(transform: PartialFunction[Result, Future[Result]])(ec: ExecutionContext): Unit =
    in.+= { result =>
      if (!transform.isDefinedAt(result)) logger.info(s"transform pipe back not defined")
      else transform(result).recover { case t => Failed(result.job, t) }(ec).foreach(result.job.results.apply)(ec)
    }(new MaybeTurn(None))

  def handleResults(results: Event[Result], iopipe: SendReceive, ec: ExecutionContext, neo: Neo) = pipeBack(results) {
    case Start(job, collection) =>
      ArchiveManipulation.applyDescription(collection, job.core.archive)
      collection.describes match {
        case Some(archive) => next(archive, job, neo, ec)(iopipe)
        case None => Future.successful(Done(job))
      }
    case Page(job, page, doc) =>
      writePage(job.core, page, doc, neo)
      next(page, job, neo, ec)(iopipe)
    case Asset(job, asset, blob) =>
      writeAsset(job.core, asset, blob, neo)
      next(asset, job, neo,ec )(iopipe)
  }(ec)

  def ensureJob(core: Core, collection: CollectionNode, context: ExecutionContext, iopipe: SendReceive, neo: Neo): Unit = {
    val job = new Job(core, new ImperativeEvent[Result])
    jobs.putIfAbsent(core, job) match {
      case Some(x) => logger.info(s"$core is already handled")
      case None => Future {
        logger.info(s"add new job $job")
        handleResults(job.results, iopipe, context, neo)
        job.results(Start(job, collection))
      }(context)
    }

  }

  def handleHints(ec: ExecutionContext, iopipe: SendReceive, neo: Neo): Unit = hints += { collection =>
    logger.info(s"got hint $collection")
    Core.get(collection.id) match {
      case None => logger.warn(s"unkonwn core ${ collection.id }")
      case Some(core) => ensureJob(core, collection, ec, iopipe, neo)
    }
  }

}
