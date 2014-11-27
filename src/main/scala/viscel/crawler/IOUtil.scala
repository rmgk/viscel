package viscel.crawler

import java.nio.file.{Files, Paths}

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import spray.client.pipelining.{Get, SendReceive, WithTransformation, WithTransformerConcatenation, addHeader, decode}
import spray.http.HttpHeaders.{Location, `Accept-Encoding`, `Content-Type`}
import spray.http.{HttpCharsets, HttpEncodings, HttpRequest, HttpResponse, Uri}
import spray.httpx.encoding._
import viscel.crawler.Result.DelayedRequest
import viscel.database.{ArchiveManipulation, Ntx}
import viscel.narration.Narrator
import viscel.shared.{AbsUri, Story}
import viscel.store.Coin
import viscel.store.Coin.{Asset, Blob, Page}
import viscel.{Deeds, sha1hex}

import scala.Predef.conforms
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object IOUtil extends StrictLogging {

	def uriToUri(in: java.net.URI): Uri = Uri.parseAbsolute(in.toString)

	private def addReferrer(referrer: Uri): (HttpRequest) => HttpRequest = addHeader("Referer" /*[sic, http spec]*/ , referrer.toString())

	def getResponse(request: HttpRequest, iopipe: SendReceive): Future[HttpResponse] = {
		val result = request ~> addHeader(`Accept-Encoding`(HttpEncodings.deflate, HttpEncodings.gzip)) ~> iopipe
		logger.info(s"get ${ request.uri } (${ request.headers })")
		result.andThen(PartialFunction(Deeds.responses.apply)).map { decode(Gzip) ~> decode(Deflate) }
	}

	def documentRequest(absuri: AbsUri)(withDoc: Document => Ntx => Strategy): DelayedRequest = {
		val uri = uriToUri(absuri)
		DelayedRequest(
			request = Get(uri),
			continue = res => withDoc(Jsoup.parse(
				res.entity.asString(defaultCharset = HttpCharsets.`UTF-8`),
				res.header[Location].fold(ifEmpty = uri)(_.uri).toString())))
	}

	def blobRequest(source: AbsUri, origin: AbsUri)(withBlob: (Array[Byte], Story.Blob) => Ntx => Strategy): DelayedRequest =
		DelayedRequest(
			request = Get(source) ~> addReferrer(uriToUri(origin)),
			continue = { res =>
				val bytes = res.entity.data.toByteArray
				withBlob(bytes,
					Story.Blob(
						sha1 = sha1hex(bytes),
						mediatype = res.header[`Content-Type`].fold("")(_.contentType.mediaType.toString())))
			})


	def writeAsset(core: Narrator, assetNode: Asset, continue: Strategy)(bytes: Array[Byte], story: Story.Blob)(ntx: Ntx): Strategy = {
		logger.debug(s"$core: received blob, applying to $assetNode")
		val path = Paths.get(viscel.hashToFilename(story.sha1))
		Files.createDirectories(path.getParent)
		Files.write(path, bytes)
		assetNode.blob_=(Blob(Coin.create(story)(ntx)))(ntx)
		continue
	}

	def writePage(core: Narrator, pageNode: Page, continue: Strategy)(doc: Document)(ntx: Ntx): Strategy = {
		logger.debug(s"$core: received ${ doc.baseUri() }, applying to $pageNode")
		implicit def tx: Ntx = ntx
		val wrapped = core.wrap(doc, pageNode.story)
		val failed = wrapped.collect { case Story.Failed(msg) => msg }.flatten
		if (failed.isEmpty) {
			ArchiveManipulation.applyNarration(pageNode.self, wrapped)
			continue
		}
		else Strategy.const(Result.Failed(failed))
	}

}
