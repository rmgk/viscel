package viscel.crawler

import org.jsoup.nodes.Document
import org.neo4j.graphdb.Node
import spray.client.pipelining.SendReceive
import viscel.Log
import viscel.crawler.IOUtil.Request
import viscel.database.Implicits.NodeOps
import viscel.database.{Neo, NeoCodec, Ntx, rel}
import viscel.narration.Narrator
import viscel.shared.Story
import viscel.shared.Story.{Asset, Failed, More}
import viscel.store.{BlobStore, Collection}

import scala.Predef.ArrowAssoc
import scala.concurrent.ExecutionContext


class Runner(narrator: Narrator, iopipe: SendReceive, collection: Collection, neo: Neo, ec: ExecutionContext) extends Runnable {

	override def toString: String = s"Job(${ narrator.toString })"

	var assets: List[(Node, Asset)] = Nil
	var pages: List[(Node, More)] = Nil
	@volatile var cancel: Boolean = false

	def collectInteresting(node: Node)(implicit ntx: Ntx): Unit = NeoCodec.load[Story](node) match {
		case m@More(loc, kind) if Archive.needsRecheck(node) => pages ::= node -> m
		case a@Asset(source, origin, metadata, None) => assets ::= node -> a
		case _ =>
	}

	def init() = synchronized {
		if (assets.isEmpty && pages.isEmpty) neo.tx { implicit ntx =>
			Archive.applyNarration(collection.self, narrator.archive)
			collection.self.next.foreach(_.fold(()) { _ => collectInteresting })
			pages = pages.take(3)
			val parent = collection.self.rightmost.above
			parent.foreach{n =>
				collectInteresting(n)
				n.above.foreach(collectInteresting)
			}
			pages = pages.distinct.sortBy(_._1.position)
		}
		else Log.error("tried to initialize non empty runner")
	}


	def handle[R](request: Request[R])(continue: R => Unit) = {
		IOUtil.getResponse(request.request, iopipe).map { response =>
			synchronized {
				val res = neo.tx { request.handler(response) }
				continue(res)
			}
		}(ec).onFailure { case t: Throwable =>
			Log.error(s"error in $narrator")
			t.printStackTrace()
			Clockwork.finish(narrator, this)
		}(ec)
	}

	override def run(): Unit = if (!cancel) synchronized {
		assets match {
			case (node, asset) :: rest =>
				assets = rest
				handle(IOUtil.blobRequest(asset.source, asset.origin) { writeAsset(narrator, node, asset) }) { _ => ec.execute(this) }
			case Nil => pages match {
				case (node, page) :: rest =>
					pages = rest
					handle(IOUtil.documentRequest(page.loc) { writePage(narrator, node, page) }) {
						case Right(failed) =>
							Log.error(s"$narrator failed on $page: $failed")
							Clockwork.finish(narrator, this)
						case Left(created) =>
							neo.tx { implicit ntx => created filterNot node.self.layerBelow.contains foreach collectInteresting }
							ec.execute(this)
					}
				case Nil =>
					Log.info(s"runner for $narrator is done")
					Clockwork.finish(narrator, this)
			}
		}
	}


	def writeAsset(core: Narrator, node: Node, asset: Asset)(blob: (Array[Byte], Story.Blob))(ntx: Ntx): Unit = {
		Log.debug(s"$core: received blob, applying to $asset ($node)")
		BlobStore.write(blob._2.sha1, blob._1)
		node.to_=(rel.blob, NeoCodec.create(blob._2)(ntx, NeoCodec.blobCodec))(ntx)
	}

	def writePage(core: Narrator, node: Node, page: More)(doc: Document)(ntx: Ntx): Either[List[Node], List[Failed]] = {
		Log.debug(s"$core: received ${
			doc.baseUri()
		}, applying to $page")
		implicit def tx: Ntx = ntx
		val wrapped = core.wrap(doc, page.kind)
		val failed = wrapped.collect {
			case f@Failed(msg) => f
		}

		if (failed.isEmpty) {
			Left(Archive.applyNarration(node, wrapped))
		}
		else {
			Right(failed)
		}
	}


}
