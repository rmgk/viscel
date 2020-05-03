package viscel.netzi

import java.io.IOException
import java.time.{Duration, Instant}
import java.time.format.DateTimeFormatter
import java.util.concurrent.{ExecutorService, TimeUnit}

import okhttp3._
import viscel.crawl.RequestException
import viscel.shared.Log
import viscel.store.v4.Vurl

import scala.concurrent.{Future, Promise}


class OkHttpRequester(maxRequests: Int, executorService: ExecutorService) extends WebRequestInterface {

  val client = {
    val connectionPool = new ConnectionPool(maxRequests * 2, 30, TimeUnit.SECONDS)
    val dispatcher     = new Dispatcher(executorService)
    dispatcher.setMaxRequests(maxRequests)

    new OkHttpClient.Builder()
      .connectTimeout(Duration.ofSeconds(30))
      .connectionPool(connectionPool)
      .dispatcher(dispatcher)
      .build()
  }

  def vreqToOkReq(vrequest: VRequest): Request = {
    val req = new Request.Builder().url(vrequest.href.uriString())
    vrequest.referer.fold(req)(ref => req.addHeader("referrer", ref.uriString()))
            .build()
  }


  override def get(request: VRequest): Future[VResponse[Either[Array[Byte], String]]] = {
    val promise   = Promise[VResponse[Either[Array[Byte], String]]]()
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
            // this may be to specific for actually parsing dates
            // https://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.3
            // http://hc.apache.org/httpcomponents-client-ga/httpclient/apidocs/org/apache/http/client/utils/DateUtils.html
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
