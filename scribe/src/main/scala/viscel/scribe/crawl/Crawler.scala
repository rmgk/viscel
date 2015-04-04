package viscel.scribe.crawl

import org.jsoup.nodes.Document
import org.neo4j.graphdb.Node
import org.scalactic.TypeCheckedTripleEquals._
import org.scalactic.{Bad, Good}
import spray.client.pipelining.SendReceive
import spray.http.{HttpRequest, HttpResponse}
import viscel.scribe.Log
import viscel.scribe.database.Archive._
import viscel.scribe.database.Implicits.NodeOps
import viscel.scribe.database._
import viscel.scribe.narration._

import scala.collection.immutable.Set
import scala.concurrent.{ExecutionContext, Future, Promise}


class Crawler(val narrator: Narrator, iopipe: SendReceive, collection: Book, neo: Neo, ec: ExecutionContext, runnerUtil: CrawlerUtil) extends Runnable {

	import runnerUtil._

	override def toString: String = s"Job(${narrator.toString})"

	var layers: List[Layer] = Nil
	var queue: List[Node] = Nil
	var recheck: Option[Node] = None
	var known: Set[Story] = Set.empty
	var recover: Boolean = true
	@volatile var cancel: Boolean = false
	val result: Promise[Boolean] = Promise()

	def unvisited(node: Node)(implicit ntx: Ntx): Boolean =
		node.hasLabel(label.More) && (node.describes eq null) ||
			(node.hasLabel(label.Asset) && (node.to(rel.blob) eq null))

	def init(): Future[Boolean] = synchronized {
		if (queue.nonEmpty || layers.nonEmpty) {
			Log.error("tried to initialize non empty runner")
			Future.failed(new IllegalStateException("already initialised"))
		}
		else neo.tx { implicit ntx =>
			if (collection.self.layer.replace(narrator.archive)) collection.invalidateSize()
			known = collectMore(collection.self).toSet
			layers = List(collection.self.layer)
			result.future
		}
	}

	def addBelow(layer: Layer, allowRedo: Boolean = false)(implicit ntx: Ntx): Unit = {
		val nodes = layer.nodes
		if (allowRedo && layers.isEmpty && layer.parent.hasLabel(label.More) && !nodes.exists(_.hasLabel(label.More)))
			queue ::= layer.parent

		val (more, other) = nodes.partition(_.hasLabel(label.More))
		val (normal, special) = more.partition(Codec.load[More](_).policy === Normal)
		val (unvisMore, visMore) = normal.partition(unvisited)
		layers = visMore.map(_.layer) ::: layers
		queue = other.filter(unvisited) ::: special ::: queue ::: unvisMore

	}

	override def run(): Unit = if (!cancel) synchronized {
		neo.tx { implicit ntx =>
			(queue, layers) match {
				case ((node :: tail), _) if node.hasLabel(label.Asset) =>
					queue = tail
					val asset = Codec.load[Asset](node)
					asset.blob.fold(ec.execute(this))(bloburl =>
						handle(request(bloburl, asset.origin)) {parseBlob _ andThen writeAsset(node, asset)})
				case ((node :: tail), _) if node.hasLabel(label.More) =>
					queue = tail
					val page = Codec.load[More](node)
					handle(request(page.loc)) {parseDocument(page.loc) _ andThen writePage(node, page)}
				case (Nil, layer :: tail) =>
					layers = tail
					addBelow(layer, allowRedo = true)
					ec.execute(this)
				case (Nil, Nil) =>
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
			queue = Nil
			layers = Nil
			recheck = None
			recover = false
			node.above.filter(_.hasLabel(label.More)).foreach(queue ::= _)
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
				// if we have changes at the end, we tests the more generating the end to make sure that has not changed
				if (changed && !wasEmpty && queue.isEmpty && layers.isEmpty)
					node.above.filter(_.hasLabel(label.More)).fold(addBelow(node.layer))(queue ::= _)
				else addBelow(node.layer)
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
