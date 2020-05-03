package viscel.netzi

import java.io.IOException
import java.time.{Duration, Instant}
import java.time.format.DateTimeFormatter

import okhttp3._
import viscel.crawl.RequestException
import viscel.shared.Log
import viscel.store.v4.Vurl

import scala.concurrent.{Future, Promise}


class OkHttpRequester() extends WebRequestInterface {

  val client = new OkHttpClient.Builder()
    .connectTimeout(Duration.ofSeconds(30))
    .build()

  def vreqToOkReq(vrequest: VRequest): Request = {
    val req = new Request.Builder().url(vrequest.href.uriString())
    vrequest.referer.fold(req)(ref => req.addHeader("referrer", ref.uriString()))
            .build()
  }


  override def get(request: VRequest): Future[VResponse[Either[Array[Byte], String]]] = {
    val promise = Promise[VResponse[Either[Array[Byte], String]]]()
    val okrequest = vreqToOkReq(request)
    Log.Crawl.info(s"request ${okrequest.url()}" + Option(okrequest.header("referrer")).fold("")(r => s" ($r)"))
    client.newCall(okrequest).enqueue(new Callback {
      override def onFailure(call: Call, e: IOException): Unit = promise.failure(e)
      override def onResponse(call: Call, response: Response): Unit = try {
        if (response.isSuccessful) {
          val body         = response.body()
          val ct           = body.contentType()
          val location     = Option(response.header("location")).getOrElse(call.request().url().toString)
          val etag         = Option(response.header("ETag"))
          val lastModified = Option(response.header("Last-Modified")).map { lm =>
            Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(lm))
          }

          val content: Either[Array[Byte], String] =
            if (ct.`type`() == "text") Right(response.body().string())
            else Left(response.body().bytes())

          promise.success(VResponse(content, Vurl.fromString(location), s"${ct.`type`()}/${ct.subtype()}", lastModified, etag))

        }
        else promise.failure(RequestException(call.request().url().toString, s"${response.code}: ${response.message()}"))
      } finally response.close()
    })
    promise.future
  }
}
