package viscel.crawler

import java.nio.file.{Files, Paths}

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.scalactic.ErrorMessage
import spray.client.pipelining.{Get, SendReceive, WithTransformation, WithTransformerConcatenation, addHeader, decode}
import spray.http.HttpHeaders.{`Content-Type`, Location, `Accept-Encoding`}
import spray.http.{HttpCharsets, HttpEncodings, HttpRequest, HttpResponse, Uri}
import spray.httpx.encoding._
import viscel.database.{ArchiveManipulation, Ntx}
import viscel.narration.Narrator
import viscel.store.Coin
import viscel.store.Coin.{Page, Asset}
import viscel.{sha1hex, Deeds}
import viscel.shared.{AbsUri, Story}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.Predef.identity
import scala.Predef.$conforms

object IOUtil extends StrictLogging {

	type ResponseHandler[A, R] = A => Ntx => Future[R]

	def uriToUri(in: java.net.URI): Uri = Uri.parseAbsolute(in.toString)

	private def addReferrer(referrer: Uri): (HttpRequest) => HttpRequest = addHeader("Referer" /*[sic, http spec]*/ , referrer.toString())

	def getResponse(request: HttpRequest, iopipe: SendReceive): Future[HttpResponse] = {
		val result = request ~> addHeader(`Accept-Encoding`(HttpEncodings.deflate, HttpEncodings.gzip)) ~> iopipe
		logger.info(s"get ${ request.uri } (${ request.headers })")
		result.andThen(PartialFunction(Deeds.responses.apply)).map { decode(Gzip) ~> decode(Deflate) }
	}

	def request[R](source: AbsUri, origin: Option[AbsUri] = None)(withResponse: ResponseHandler[HttpResponse, R]): Request[R] = {
		Req(Get(uriToUri(source)) ~> origin.fold[HttpRequest => HttpRequest](identity)(origin => addReferrer(uriToUri(origin))), withResponse)
	}

	def documentRequest[R](absuri: AbsUri)(withDocument: ResponseHandler[Document, R]): Request[R] = request(absuri) { res =>
		withDocument(Jsoup.parse(
			res.entity.asString(defaultCharset = HttpCharsets.`UTF-8`),
			res.header[Location].fold(ifEmpty = uriToUri(absuri))(_.uri).toString()))
	}


	def blobRequest[R](source: AbsUri, origin: AbsUri)(withBlob: ResponseHandler[(Array[Byte], Story.Blob), R]): Request[R] =
		request(source, Some(origin)) { res =>
			val bytes = res.entity.data.toByteArray
			withBlob((bytes,
				Story.Blob(
					sha1 = sha1hex(bytes),
					mediatype = res.header[`Content-Type`].fold("")(_.contentType.mediaType.toString()))))
		}

}
