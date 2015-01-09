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

	final case class Request[R](request: HttpRequest, handler: HttpResponse => Ntx => R)

	val digester = MessageDigest.getInstance("SHA1")

	def sha1hex(b: Array[Byte]) = Predef.wrapByteArray(digester.digest(b)).map { h => f"$h%02x" }.mkString

	type Handler[A, R] = A => Ntx => R

	def uriToUri(in: java.net.URI): Uri = Uri.parseAbsolute(in.toString)

	def addReferrer(referrer: Uri): (HttpRequest) => HttpRequest = addHeader("Referer" /*[sic, http spec]*/ , referrer.toString())

	def getResponse(request: HttpRequest, iopipe: SendReceive): Future[HttpResponse] = {
		val result = request ~> addHeader(`Accept-Encoding`(HttpEncodings.deflate, HttpEncodings.gzip)) ~> iopipe
		Log.info(s"get ${ request.uri } (${ request.headers })")
		result.andThen(PartialFunction(Deeds.responses.apply)).map { decode(Gzip) ~> decode(Deflate) }
	}

	def request[R](source: AbsUri, origin: Option[AbsUri] = None)(withResponse: Handler[HttpResponse, R]): Request[R] = {
		Request(Get(uriToUri(source)) ~> origin.fold[HttpRequest => HttpRequest](identity)(origin => addReferrer(uriToUri(origin))), withResponse)
	}
	
	def parseResponse(absUri: AbsUri)(res: HttpResponse): Document = Jsoup.parse(
		res.entity.asString(defaultCharset = HttpCharsets.`UTF-8`),
		res.header[Location].fold(ifEmpty = uriToUri(absUri))(_.uri).toString())

	def documentRequest[R](absUri: AbsUri)(withDocument: Handler[Document, R]): Request[R] =
		request(absUri) { parseResponse(absUri) andThen withDocument }


	def blobRequest[R](source: AbsUri, origin: AbsUri)(withBlob: Handler[(Array[Byte], Story.Blob), R]): Request[R] =
		request(source, Some(origin)) { res =>
			val bytes = res.entity.data.toByteArray
			withBlob((bytes,
				Story.Blob(
					sha1 = sha1hex(bytes),
					mediatype = res.header[`Content-Type`].fold("")(_.contentType.mediaType.toString()))))
		}

}
