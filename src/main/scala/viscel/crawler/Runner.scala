package viscel.crawler

import java.nio.file.{Files, Paths}

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.jsoup.nodes.Document
import org.neo4j.graphdb.Node
import org.scalactic.ErrorMessage
import spray.client.pipelining.SendReceive
import viscel.database.{ArchiveManipulation, Neo, NodeOps, Ntx}
import viscel.narration.Narrator
import viscel.shared.Story
import viscel.store.Coin.{Asset, Page}
import viscel.store.{Coin, Collection}

import scala.Predef.conforms
import scala.annotation.tailrec
import scala.collection.JavaConverters.iterableAsScalaIterableConverter
import scala.concurrent.{ExecutionContext, Future}


class Runner(narrator: Narrator, iopipe: SendReceive, collection: Collection, neo: Neo, ec: ExecutionContext) extends StrictLogging {

	override def toString: String = s"Job(${ narrator.toString })"

	def start(): Future[List[ErrorMessage]] = {
		neo.tx { ArchiveManipulation.applyNarration(collection.self, narrator.archive)(_) }
		run(strategy(collection.self))
	}

	private def run[R](r: Ntx => Request[R]): Future[R] = {
		neo.tx { r } match {
			case Req(request, handler) =>
				IOUtil.getResponse(request, iopipe).flatMap { res =>
					neo.tx { handler(res) }
				}(ec)
			case RequestDone(result) => Future.successful(result)

		}
	}


	def strategy(state: Node)(ntx: Ntx): Request[List[ErrorMessage]] = {
		val selected = repeat(state, _.next(ntx), unseenOnly(shallow = true)(ntx))
		selected match {
			case Some(Coin.isPage(page)) =>
				IOUtil.documentRequest(page.location(ntx)) { doc => ntx =>
					val errors = writePage(narrator, page)(doc)(ntx)
					page.self.next(ntx).fold[Future[List[ErrorMessage]]](Future.successful(Nil))(n => run(strategy(n)))
				}
			case Some(Coin.isAsset(asset)) => IOUtil.blobRequest(asset.source(ntx), asset.origin(ntx)) { blob => ntx =>
				writeAsset(narrator, asset)(blob)(ntx)
					asset.self.next(ntx).fold[Future[List[ErrorMessage]]](Future.successful(Nil))(n => run(strategy(n)))
			}
			case Some(other) => RequestDone(s"can only request pages and assets not ${ other.getLabels.asScala.toList }" :: Nil)
			case None => RequestDone(Nil)
		}

	}


	def writeAsset[R](core: Narrator, assetNode: Asset)(blob: (Array[Byte], Story.Blob))(ntx: Ntx): List[ErrorMessage] = {
		logger.debug(s"$core: received blob, applying to $assetNode")
		val path = Paths.get(viscel.hashToFilename(blob._2.sha1))
		Files.createDirectories(path.getParent)
		Files.write(path, blob._1)
		assetNode.blob_=(Coin.Blob(Coin.create(blob._2)(ntx)))(ntx)
		Nil
	}

	def writePage(core: Narrator, pageNode: Page)(doc: Document)(ntx: Ntx): List[ErrorMessage] = {
		logger.debug(s"$core: received ${
			doc.baseUri()
		}, applying to $pageNode")
		implicit def tx: Ntx = ntx
		val wrapped = core.wrap(doc, pageNode.story)
		val failed = wrapped.collect {
			case Story.Failed(msg) => msg
		}.flatten
		if (failed.isEmpty) {
			ArchiveManipulation.applyNarration(pageNode.self, wrapped)
		}
		failed
	}

	type Select = Ntx => Node => Option[Node]

	def unseenOnly(shallow: Boolean): Select = ntx => {
		case n@Coin.isPage(page) if page.self.describes(ntx) eq null => Some(n)
		case n@Coin.isAsset(asset) if (!shallow) && asset.blob(ntx).isEmpty => Some(n)
		case _ => None
	}

	@tailrec
	final def repeat[R](n: Node, f: Node => Option[Node], s: Node => Option[R]): Option[R] = (s(n), f(n)) match {
		case (r@Some(_), _) => r
		case (None, Some(next)) => repeat(next, f, s)
		case (None, None) => None
	}

}
