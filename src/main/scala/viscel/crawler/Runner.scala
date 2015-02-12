package viscel.crawler

import org.jsoup.nodes.Document
import org.neo4j.graphdb.Node
import org.scalactic.{Bad, Good}
import spray.client.pipelining.SendReceive
import spray.http.{HttpRequest, HttpResponse}
import viscel.Log
import viscel.crawler.Archive._
import viscel.crawler.RunnerUtil._
import viscel.database.Implicits.NodeOps
import viscel.database.{Book, Neo, NeoCodec, Ntx, rel}
import viscel.narration.Narrator
import viscel.shared.Story
import viscel.shared.Story.More.Issue
import viscel.shared.Story.{Asset, More}

import scala.Predef.ArrowAssoc
import scala.Predef.implicitly
import scala.collection.immutable.Set
import scala.concurrent.ExecutionContext


class Runner(narrator: Narrator, iopipe: SendReceive, val collection: Book, neo: Neo, ec: ExecutionContext) extends Runnable {

	override def toString: String = s"Job(${ narrator.toString })"

	var assets: List[(Node, Asset)] = Nil
	var volumes: List[(Node, More)] = Nil
	var pages: List[(Node, More)] = Nil
	var recheck: Option[Node] = None
	var known: Set[Story] = Set.empty
	var recover: Boolean = true
	@volatile var cancel: Boolean = false

	def collectUnvisited(node: Node)(implicit ntx: Ntx): Unit = NeoCodec.load[Story](node) match {
		case m@More(loc, Story.More.Archive | Issue) if node.describes eq null => volumes ::= node -> m
		case m@More(loc, kind) if node.describes eq null => pages ::= node -> m
		case a@Asset(source, origin, metadata, None) => assets ::= node -> a
		case _ =>
	}

	def init() = synchronized {
		if (assets.isEmpty && pages.isEmpty) neo.tx { implicit ntx =>
			applyNarration(collection.self, narrator.archive)
			known = collectMore(collection.self).toSet
			collection.self.fold(()) { _ => collectUnvisited }
			pages = pages.reverse
			assets = assets.reverse
			if (pages.isEmpty) {
				recheck = Some(collection.self)
			}
		}
		else Log.error("tried to initialize non empty runner")
	}

	def recheckOrDone(): Unit = recheck.flatMap(n => neo.tx(nextHub(n)(_))) match {
		case None =>
			Log.info(s"runner for $narrator is done")
			if (recover) Clockwork.updateDates(narrator)
			Clockwork.finish(narrator, this)
		case sn@Some(node) =>
			recheck = sn
			val m = neo.tx(NeoCodec.load[More](node)(_, implicitly))
			pages ::= node -> m
			ec.execute(this)
	}

	override def run(): Unit = if (!cancel) synchronized {
		assets match {
			case (node, asset) :: rest =>
				assets = rest
				handle(request(asset.source, Some(asset.origin))) { parseBlob _ andThen writeAsset(node, asset) }
			case Nil => volumes match {
				case (node, volume) :: rest =>
					volumes = rest
					handle(request(volume.loc)) { parseDocument(volume.loc) _ andThen writePage(node, volume) }
				case Nil => pages match {
					case (node, page) :: rest =>
						pages = rest
						handle(request(page.loc)) { parseDocument(page.loc) _ andThen writePage(node, page) }
					case Nil =>
						recheckOrDone()
				}
			}
		}
	}

	def handle[T, R](request: HttpRequest)(handler: HttpResponse => Ntx => R) = {
		getResponse(request, iopipe).map { response =>
			synchronized { neo.tx { handler(response) } }
		}(ec).onFailure { case t: Throwable =>
			Log.error(s"error in $narrator")
			t.printStackTrace()
			Clockwork.finish(narrator, this)
		}(ec)
	}

	def tryRecovery(node: Node)(implicit ntx: Ntx) =
		if (!recover) {
			Log.info(s"no more recovery after failure in $narrator at $node")
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

	def writeAsset(node: Node, asset: Asset)(blob: Story.Blob)(ntx: Ntx): Unit = {
		Log.debug(s"$narrator: received blob, applying to $asset ($node)")
		node.to_=(rel.blob, NeoCodec.create(blob)(ntx, NeoCodec.blobCodec))(ntx)
		ec.execute(this)
	}

	def writePage(node: Node, page: More)(doc: Document)(ntx: Ntx): Unit = {
		Log.debug(s"$narrator: received ${
			doc.baseUri()
		}, applying to $page")
		implicit def tx: Ntx = ntx
		narrator.wrap(doc, page.kind) match {
			case Good(wrapped) =>
				val wasEmpty = node.layer.isEmpty
				val filter = known.diff(collectMore(node).reverse.tail.toSet)
				val filtered = wrapped filterNot filter
				val changed = applyNarration(node, filtered)
				if (changed) {
					known = collectMore(collection.self).toSet
					// remove cached size
					collection.self.removeProperty("size")
					// if we have changes at the end, we tests the more generating the end to make sure that has not changed
					if (!wasEmpty && pages.isEmpty) parentMore(node.prev).foreach(pages ::= _)
					node.layerBelow.reverse foreach collectUnvisited
				}
				ec.execute(this)
		case Bad(failed) =>
			Log.error(s"$narrator failed on $page: $failed")
			tryRecovery(node)(ntx)
			Clockwork.finish(narrator, this)
		}
	}


}
