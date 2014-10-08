package viscel.crawler

import com.typesafe.scalalogging.slf4j.StrictLogging
import rescala.events.{Event, ImperativeEvent}
import spray.client.pipelining._
import viscel.cores.Core
import viscel.store._

import scala.collection.concurrent
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}


object Clockwork extends StrictLogging {

  val jobs: concurrent.Map[Core, Job] = concurrent.TrieMap[Core, Job]()

  val archiveHint = new ImperativeEvent[ArchiveNode]()
  val collectionHint = new ImperativeEvent[CollectionNode]()

  val hints: Event[CollectionNode] = archiveHint.map((_: ArchiveNode).collection) || collectionHint

  def ensureJob(core: Core, collection: CollectionNode, ec: ExecutionContext, iopipe: SendReceive, neo: Neo): Unit = {
    val job = new Job(core, neo, iopipe, ec)
    jobs.putIfAbsent(core, job) match {
      case Some(x) => logger.info(s"$core is already running")
      case None =>
        logger.info(s"add new job $job")
        job.start(collection).onComplete {
          case Success(_) => logger.info(s"$job completed successfully")
          case Failure(t) => logger.warn(s"$job failed with $t")
        }(ec)
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
