package viscel.scribe.crawl

import org.jsoup.nodes.Document
import org.neo4j.graphdb.Node
import org.scalactic.{Bad, Good}
import spray.client.pipelining.SendReceive
import spray.http.{HttpRequest, HttpResponse}
import viscel.scribe.Log
import viscel.scribe.database.Implicits.NodeOps
import viscel.scribe.database._
import viscel.scribe.narration._

import scala.collection.immutable.Set
import scala.concurrent.{ExecutionContext, Future, Promise}


class Crawler(val narrator: Narrator, iopipe: SendReceive, collection: Book, neo: Neo, ec: ExecutionContext, runnerUtil: CrawlerUtil) extends Runnable {

	import runnerUtil._

	override def toString: String = s"Job(${narrator.toString})"

	var queue: CrawlQueue = null
	var recheck: Option[Node] = None
	var known: Set[Story] = Set.empty
	var recover: Boolean = true
	@volatile var cancel: Boolean = false
	val result: Promise[Boolean] = Promise()


	def collectMore(start: Node)(implicit ntx: Ntx): List[More] = start.layer.recursive.collect {
		case n if n.hasLabel(label.More) => Codec.load[More](n)
	}

	def init(): Future[Boolean] = synchronized {
		if (queue.isEmpty()) {
			Log.error("tried to initialize non empty runner")
			Future.failed(new IllegalStateException("already initialised"))
		}
		else neo.tx { implicit ntx =>
			if (collection.self.layer.replace(narrator.archive)) collection.invalidateSize()
			known = collectMore(collection.self).toSet
			queue = new CrawlQueue(collection.self.layer)
			result.future
		}
	}


	override def run(): Unit = if (!cancel) synchronized {
		neo.tx { implicit ntx =>
			queue.deque() match {
				case Some(node) if node.hasLabel(label.Asset) =>
					val asset = Codec.load[Asset](node)
					asset.blob.fold(ec.execute(this))( bloburl =>
						handle(request(bloburl, asset.origin)) {parseBlob _ andThen writeAsset(node, asset)})
				case Some(node)  if node.hasLabel(label.More) =>
					val page = Codec.load[More](node)
					handle(request(page.loc)) {parseDocument(page.loc) _ andThen writePage(node, page)}
				case None =>
					result.success(true)
			}
		}
	}

	def handle[T](request: HttpRequest)(handler: HttpResponse => Ntx => Unit): Unit = {
		getResponse(request, iopipe).map { response =>
			synchronized {neo.tx {handler(response)}}
		}(ec).onFailure {PartialFunction(result.failure)}(ec)
	}

	def tryRecovery(node: Node)(implicit ntx: Ntx) =
		if (!recover) {
			Log.info(s"no more recovery after failure in $narrator at $node")
			result.success(false)
		}
		else {
			Log.info(s"trying to recover after failure in $narrator at $node")
			queue.drain()
			recheck = None
			recover = false
			node.above.filter(_.hasLabel(label.More)).foreach(queue.redo)
			ec.execute(this)
		}

	def writeAsset(node: Node, asset: Asset)(blob: Blob)(ntx: Ntx): Unit = {
		Log.debug(s"$narrator: received blob, applying to $asset ($node)")
		node.to_=(rel.blob, Codec.create(blob)(ntx, Codec.blobCodec))(ntx)
		ec.execute(this)
	}

	def writePage(node: Node, page: More)(doc: Document)(ntx: Ntx): Unit = {
		Log.debug(s"$narrator: received ${doc.baseUri()}, applying to $page")
		implicit def tx: Ntx = ntx
		narrator.wrap(doc, page) match {
			case Good(wrapped) =>
				val wasEmpty = node.layer.isEmpty
				val filter = known.diff(collectMore(node).toSet)
				val filtered = wrapped filterNot filter
				val changed = node.layer.replace(filtered)
				// if we have changes at the end, we test the more generating the end to make sure that has not changed
				if (changed && !wasEmpty && queue.isEmpty) {
					node.above.filter(_.hasLabel(label.More)).fold(queue.addBelow(node.layer))(queue.redo)
				}
				else queue.addBelow(node.layer)
				if (changed) {
					known = collectMore(collection.self).toSet
					// remove cached size
					collection.invalidateSize()
				}
				ec.execute(this)
			case Bad(failed) =>
				Log.error(s"$narrator failed on $page: ${failed.map {_.describe}}")
				tryRecovery(node)(ntx)
		}
	}


}
