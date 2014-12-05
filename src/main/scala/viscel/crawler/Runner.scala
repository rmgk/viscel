package viscel.crawler

import org.jsoup.nodes.Document
import org.neo4j.graphdb.Node
import org.scalactic.ErrorMessage
import spray.client.pipelining.SendReceive
import viscel.Log
import viscel.database.Implicits.NodeOps
import viscel.database.{ArchiveManipulation, Neo, Ntx, Util}
import viscel.narration.Narrator
import viscel.shared.Story
import viscel.store.Coin.{Asset, Page}
import viscel.store.{Cache, Coin, Collection}

import scala.Predef.$conforms
import scala.annotation.tailrec
import scala.collection.JavaConverters.iterableAsScalaIterableConverter
import scala.concurrent.{ExecutionContext, Future}


class Runner(narrator: Narrator, iopipe: SendReceive, collection: Collection, neo: Neo, ec: ExecutionContext) {

	override def toString: String = s"Job(${ narrator.toString })"

	def start(): Future[List[ErrorMessage]] = {
		neo.tx { ArchiveManipulation.applyNarration(collection.self, narrator.archive)(_) }
		run(strategy(collection.self, nextSelect))
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

	type Select = Ntx => Node => Option[Node]



	def continueOrDone(nopt: Option[Node], select: Select, ntx: Ntx): Future[List[ErrorMessage]] =
		nopt.fold[Future[List[ErrorMessage]]](Future.successful(Nil))(n => run(strategy(n.self, select)))


	val nextSelect: Select = ntx => n => repeat(n.next(ntx), _.next(ntx), unseenOnly(shallow = false)(ntx))
	val forcePrevPage: Select = ntx => state => repeat(state.prev(ntx), _.prev(ntx), Coin.isPage).map(_.self)

	def strategy(state: Node, select: Select)(ntx: Ntx): Request[List[ErrorMessage]] = {
		val selected = select(ntx)(state)
		selected match {
			case Some(Coin.isPage(page)) =>
				IOUtil.documentRequest(page.location(ntx)) { doc => ntx =>
					val errors = writePage(narrator, page)(doc)(ntx)
					if (errors.nonEmpty) {
						continueOrDone(forcePrevPage(ntx)(page.self), select, ntx)
					}
					else {
						continueOrDone(Some(page.self), select, ntx)
					}
				}
			case Some(Coin.isAsset(asset)) => IOUtil.blobRequest(asset.source(ntx), asset.origin(ntx)) { blob => ntx =>
				writeAsset(narrator, asset)(blob)(ntx)
				continueOrDone(Some(asset.self), select, ntx)
			}
			case Some(other) => RequestDone(s"can only request pages and assets not ${ other.getLabels.asScala.toList }" :: Nil)
			case None => RequestDone(Nil)
		}

	}


	def writeAsset[R](core: Narrator, assetNode: Asset)(blob: (Array[Byte], Story.Blob))(ntx: Ntx): List[ErrorMessage] = {
		Log.debug(s"$core: received blob, applying to $assetNode")
		Cache.write(blob._2.sha1, blob._1)

		assetNode.blob_=(Coin.Blob(Coin.create(blob._2)(ntx)))(ntx)
		Nil
	}

	def writePage(core: Narrator, pageNode: Page)(doc: Document)(ntx: Ntx): List[ErrorMessage] = {
		Log.debug(s"$core: received ${
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

	def unseenOnly(shallow: Boolean): Select = ntx => {
		case n@Coin.isPage(page) if page.self.describes(ntx) eq null => Some(n)
		case n@Coin.isAsset(asset) if (!shallow) && asset.blob(ntx).isEmpty => Some(n)
		case _ => None
	}

	val recheckOld: Select = ntx => {
		case n@Coin.isPage(page) if Util.needsRecheck(n)(ntx) || (page.self.describes(ntx) eq null) => Some(n)
		case n@Coin.isAsset(asset) if asset.blob(ntx).isEmpty => Some(n)
		case _ => None
	}

	@tailrec
	final def repeat[R](n: Option[Node], f: Node => Option[Node], s: Node => Option[R]): Option[R] = (n flatMap s, n flatMap f) match {
		case (r@Some(_), _) => r
		case (None, next @ Some(_)) => repeat(next, f, s)
		case (None, None) => None
	}

}
