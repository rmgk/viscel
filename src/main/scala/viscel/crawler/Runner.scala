package viscel.crawler

import org.jsoup.nodes.Document
import org.neo4j.graphdb.Node
import spray.client.pipelining.SendReceive
import spray.http.{HttpRequest, HttpResponse}
import viscel.Log
import viscel.database.Implicits.NodeOps
import viscel.database.{Neo, NeoCodec, Ntx, label, rel}
import viscel.narration.Narrator
import viscel.shared.Story
import viscel.shared.Story.{Asset, Failed, More}
import viscel.store.{BlobStore, Collection}

import scala.Predef.ArrowAssoc
import scala.Predef.implicitly
import scala.annotation.tailrec
import scala.concurrent.ExecutionContext

import viscel.crawler.RunnerUtil._


class Runner(narrator: Narrator, iopipe: SendReceive, val collection: Collection, neo: Neo, ec: ExecutionContext) extends Runnable {

	override def toString: String = s"Job(${ narrator.toString })"

	var assets: List[(Node, Asset)] = Nil
	var pages: List[(Node, More)] = Nil
	var recheck: Node = collection.self
	@volatile var cancel: Boolean = false

	def collectUnvisited(node: Node)(implicit ntx: Ntx): Unit = NeoCodec.load[Story](node) match {
		case m@More(loc, kind) if (node.describes eq null) || kind.contains("volatile") => pages ::= node -> m
		case a@Asset(source, origin, metadata, None) => assets ::= node -> a
		case _ =>
	}

	def init() = synchronized {
		if (assets.isEmpty && pages.isEmpty) neo.tx { implicit ntx =>
			Archive.applyNarration(collection.self, narrator.archive)
			collection.self.next.foreach(_.fold(()) { _ => collectUnvisited })
		}
		else Log.error("tried to initialize non empty runner")
	}

	def recheckOrDone(): Unit = neo.tx(nextHub(recheck)(_)) match {
		case None =>
			Log.info(s"runner for $narrator is done")
			neo.tx(Archive.updateDates(collection.self)(_))
			Clockwork.finish(narrator, this)
		case Some(node) =>
			recheck = node
			val m = neo.tx(NeoCodec.load[More](node)(_, implicitly))
			pages ::= node -> m
			ec.execute(this)
	}

	override def run(): Unit = if (!cancel) synchronized {
		assets match {
			case (node, asset) :: rest =>
				assets = rest
				handle(request(asset.source, Some(asset.origin))) { parseBlob _ andThen writeAsset(narrator, node, asset) }
			case Nil => pages match {
				case (node, page) :: rest =>
					pages = rest
					handle(request(page.loc)) { parseDocument(page.loc) _ andThen writePage(narrator, node, page) }
				case Nil =>
					recheckOrDone()
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

	def writeAsset(core: Narrator, node: Node, asset: Asset)(blob: (Array[Byte], Story.Blob))(ntx: Ntx): Unit = {
		Log.debug(s"$core: received blob, applying to $asset ($node)")
		BlobStore.write(blob._2.sha1, blob._1)
		node.to_=(rel.blob, NeoCodec.create(blob._2)(ntx, NeoCodec.blobCodec))(ntx)
		ec.execute(this)
	}

	def writePage(core: Narrator, node: Node, page: More)(doc: Document)(ntx: Ntx): Unit = {
		Log.debug(s"$core: received ${
			doc.baseUri()
		}, applying to $page")
		implicit def tx: Ntx = ntx
		val wrapped = core.wrap(doc, page.kind)
		val failed = wrapped.collect {
			case f@Failed(msg) => f
		}

		if (failed.isEmpty) {
			val wasEmpty = node.layer.isEmpty
			val changed = Archive.applyNarration(node, wrapped)
			if (changed) {
				// remove cached size
				collection.self.removeProperty("size")
				if (!wasEmpty && pages.isEmpty) previousMore(node.prev).foreach(pages ::= _)
				node.layerBelow foreach collectUnvisited
			}
			ec.execute(this)
		}
		else {
			Log.error(s"$narrator failed on $page: $failed")
			Clockwork.finish(narrator, this)
		}
	}


}
