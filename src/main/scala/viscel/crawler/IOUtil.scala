package viscel.crawler

import java.security.MessageDigest

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import rescala.propagation.Engines.default
import spray.client.pipelining.{Get, SendReceive, WithTransformation, WithTransformerConcatenation, addHeader, decode}
import spray.http.HttpHeaders.{Location, `Accept-Encoding`, `Content-Type`}
import spray.http.{HttpCharsets, HttpEncodings, HttpRequest, HttpResponse, Uri}
import spray.httpx.encoding._
import viscel.database.Ntx
import viscel.shared.{AbsUri, Story}
import viscel.{Deeds, Log}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.Predef.identity


object IOUtil {

	val digester = MessageDigest.getInstance("SHA1")

	def sha1hex(b: Array[Byte]) = Predef.wrapByteArray(digester.digest(b)).map { h => f"$h%02x" }.mkString

	type ResponseHandler[A, R] = A => Ntx => Future[R]

	def uriToUri(in: java.net.URI): Uri = Uri.parseAbsolute(in.toString)

	def addReferrer(referrer: Uri): (HttpRequest) => HttpRequest = addHeader("Referer" /*[sic, http spec]*/ , referrer.toString())

	def getResponse(request: HttpRequest, iopipe: SendReceive): Future[HttpResponse] = {
		val result = request ~> addHeader(`Accept-Encoding`(HttpEncodings.deflate, HttpEncodings.gzip)) ~> iopipe
		Log.info(s"get ${ request.uri } (${ request.headers })")
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
