package viscel.crawler

import java.nio.file.{Files, Paths}

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.scalactic.TypeCheckedTripleEquals._
import spray.client.pipelining.{Get, SendReceive, WithTransformation, WithTransformerConcatenation, addHeader, decode}
import spray.http.HttpHeaders.{Location, `Accept-Encoding`, `Content-Type`}
import spray.http.Uri.Query
import spray.http.{HttpCharsets, HttpEncodings, HttpRequest, HttpResponse, MediaType, Uri}
import spray.httpx.encoding._
import viscel.database.{rel, ArchiveManipulation, Ntx, NodeOps}
import viscel.narration.Narrator
import viscel.shared.{Story, AbsUri}
import viscel.store.Coin.{Page, Asset, Blob}
import viscel.{Deeds, sha1hex}
import viscel.store.{Coin}
import viscel.crawler.Result.DelayedRequest

import scala.Predef.any2ArrowAssoc
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

object IOUtil extends StrictLogging {

	def uriToUri(in: java.net.URI): Uri = Uri.from(in.getScheme, in.getUserInfo, in.getHost, in.getPort, in.getPath, Query.apply(in.getQuery), Option(in.getFragment))

	private def addReferrer(referrer: Uri): (HttpRequest) => HttpRequest = addHeader("Referer" /*[sic, http spec]*/ , referrer.toString())

	def getResponse(request: HttpRequest, iopipe: SendReceive): Future[HttpResponse] = {
		val result = request ~> addHeader(`Accept-Encoding`(HttpEncodings.deflate, HttpEncodings.gzip)) ~> iopipe
		logger.info(s"get ${ request.uri } (${ request.headers })")
		result.andThen(PartialFunction(Deeds.responses.apply)).map { decode(Gzip) ~> decode(Deflate) }
	}

	def documentRequest(absuri: AbsUri): DelayedRequest[Document] = {
		val uri = uriToUri(absuri)
		DelayedRequest(
			request = Get(uri),
			continue = res => Jsoup.parse(
				res.entity.asString(defaultCharset = HttpCharsets.`UTF-8`),
				res.header[Location].fold(ifEmpty = uri)(_.uri).toString()))
	}

	def blobRequest(source: AbsUri, origin: AbsUri): DelayedRequest[(Story.Blob, Array[Byte])] =
		DelayedRequest(
			request = Get(source) ~> addReferrer(uriToUri(origin)),
			continue = { res =>
				val bytes = res.entity.data.toByteArray
				Story.Blob(
					sha1 = sha1hex(bytes),
					mediatype = res.header[`Content-Type`].fold("")(_.contentType.mediaType.toString())) -> bytes
			})


	def writeAsset(core: Narrator, assetNode: Asset)(blob: (Story.Blob, Array[Byte]))(ntx: Ntx): Unit = {
		logger.debug(s"$core: received blob, applying to $assetNode")
		val path = Paths.get(viscel.hashToFilename(blob._1.sha1))
		Files.createDirectories(path.getParent)
		Files.write(path, blob._2)
		assetNode.blob_=(Blob(Coin.create(blob._1)(ntx)))(ntx)
	}

	def writePage(core: Narrator, pageNode: Page)(doc: Document)(ntx: Ntx): Unit = {
		logger.debug(s"$core: received ${ doc.baseUri() }, applying to $pageNode")
		implicit def tx: Ntx = ntx
		ArchiveManipulation.applyNarration(pageNode.self, core.wrap(doc, pageNode.story))
	}

}
