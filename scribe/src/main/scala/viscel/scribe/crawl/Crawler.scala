package viscel.scribe.crawl

import org.jsoup.nodes.Document
import org.neo4j.graphdb.Node
import org.scalactic.{Bad, Good}
import spray.client.pipelining.SendReceive
import spray.http.{HttpRequest, HttpResponse}
import viscel.scribe.Log
import viscel.scribe.database.Archive._
import viscel.scribe.database.Implicits.NodeOps
import viscel.scribe.database.{Book, Codec, Neo, Ntx, label, rel}
import viscel.scribe.narration.{Asset, Blob, More, Narrator, Story, Volatile}

import scala.Predef.ArrowAssoc
import scala.collection.immutable.Set
import scala.concurrent.{ExecutionContext, Future, Promise}


class Crawler(val narrator: Narrator, iopipe: SendReceive, collection: Book, neo: Neo, ec: ExecutionContext, runnerUtil: CrawlerUtil) extends Runnable {

	import runnerUtil._

	override def toString: String = s"Job(${narrator.toString})"

	var assets: List[(Node, Asset)] = Nil
	var volumes: List[(Node, More)] = Nil
	var pages: List[(Node, More)] = Nil
	var recheck: Option[Node] = None
	var known: Set[Story] = Set.empty
	var recover: Boolean = true
	@volatile var cancel: Boolean = false
	val result: Promise[Boolean] = Promise()

	def collectUnvisited(node: Node)(implicit ntx: Ntx): Unit =
		if (node.hasLabel(label.More) || node.hasLabel(label.Asset))
			Codec.load[Story](node) match {
				case m@More(_, Volatile, _) if node.describes eq null => volumes ::= node -> m
				case m@More(_, _, _) if node.describes eq null => pages ::= node -> m
				case a@Asset(Some(_), _, _, _) if node.to(rel.blob) eq null => assets ::= node -> a
				case _ =>
			}

	def init(): Future[Boolean] = synchronized {
		if (assets.isEmpty && pages.isEmpty) neo.tx { implicit ntx =>
			if (applyNarration(collection.self, narrator.archive)) collection.invalidateSize()
			known = collectMore(collection.self).toSet
			collection.self.fold(()) { _ => collectUnvisited }
			pages = pages.reverse
			assets = assets.reverse
			if (pages.isEmpty) {
				recheck = Some(collection.self)
			}
			result.future
		}
		else {
			Log.error("tried to initialize non empty runner")
			Future.failed(new IllegalStateException("already initialised"))
		}
	}

	def recheckOrDone(): Unit = recheck.flatMap(n => neo.tx(nextHub(n)(_))) match {
		case None => result.success(true)
		case sn@Some(node) =>
			recheck = sn
			val m = neo.tx(Codec.load(node)(_, Codec.moreCodec))
			pages ::= node -> m
			ec.execute(this)
	}

	override def run(): Unit = if (!cancel) synchronized {
		assets match {
			case (node, asset) :: rest =>
				assets = rest
				handle(request(asset.blob.get, asset.origin)) {parseBlob _ andThen writeAsset(node, asset)}
			case Nil => volumes match {
				case (node, volume) :: rest =>
					volumes = rest
					handle(request(volume.loc)) {parseDocument(volume.loc) _ andThen writePage(node, volume)}
				case Nil => pages match {
					case (node, page) :: rest =>
						pages = rest
						handle(request(page.loc)) {parseDocument(page.loc) _ andThen writePage(node, page)}
					case Nil =>
						recheckOrDone()
				}
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
			volumes = Nil
			pages = Nil
			assets = Nil
			recheck = None
			recover = false
			parentMore(node.prev).foreach(pages ::= _)
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
				val filter = known.diff(collectMore(node).reverse.tail.toSet)
				val filtered = wrapped filterNot filter
				val changed = applyNarration(node, filtered)
				if (changed) {
					known = collectMore(collection.self).toSet
					// remove cached size
					collection.invalidateSize()
					// if we have changes at the end, we tests the more generating the end to make sure that has not changed
					if (!wasEmpty && pages.isEmpty) parentMore(node.prev).foreach(pages ::= _)
					node.layerBelow.reverse foreach collectUnvisited
				}
				ec.execute(this)
			case Bad(failed) =>
				Log.error(s"$narrator failed on $page: ${failed.map{_.describe}}")
				tryRecovery(node)(ntx)
		}
	}


}
