package viscel.netzi

import java.io.IOException
import java.time.{Duration, Instant}
import java.time.format.DateTimeFormatter
import java.util.concurrent.{ExecutorService, TimeUnit}

import okhttp3._
import viscel.crawl.RequestException
import viscel.shared.{Log, Vurl}

import scala.concurrent.{Future, Promise}


class OkHttpRequester(maxRequests: Int, requestsPerHost: Int, val executorService: ExecutorService) extends WebRequestInterface {

  val referrer = "Referer"

  val client = {
    val connectionPool = new ConnectionPool(maxRequests * 2, 30, TimeUnit.SECONDS)
    val dispatcher     = new Dispatcher(executorService)
    dispatcher.setMaxRequests(maxRequests)
    dispatcher.setMaxRequestsPerHost(requestsPerHost)

    val generalTimeout = Duration.ofSeconds(60)

    new OkHttpClient.Builder()
      .connectTimeout(generalTimeout)
      .readTimeout(generalTimeout)
      .callTimeout(Duration.ofSeconds(0))
      .connectionPool(connectionPool)
      .dispatcher(dispatcher)
      .build()
  }

  def vreqToOkReq(vrequest: VRequest): Request = {
    val req = new Request.Builder().url(vrequest.href.uriString())
    vrequest.referer.fold(req)(ref => req.addHeader(referrer, ref.uriString()))
      .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:68.0) Gecko/20100101 Firefox/68.0")
            .build()
  }


  override def get(request: VRequest): Future[VResponse[Either[Array[Byte], String]]] = {
    val promise   = Promise[VResponse[Either[Array[Byte], String]]]()
    val okrequest = vreqToOkReq(request)
    Log.Crawl.info(s"request ${okrequest.url()}" + Option(okrequest.header(referrer)).fold("")(r => s" ($r)"))
    client.newCall(okrequest).enqueue(new Callback {
      override def onFailure(call: Call, e: IOException): Unit = promise.failure(e)
      override def onResponse(call: Call, response: Response): Unit = try {
        if (response.isSuccessful) {
          val body = response.body()
          val ct   = body.contentType()
          val location = response.request().url().toString
          val etag         = Option(response.header("ETag"))
          val lastModified = Option(response.header("Last-Modified")).map { lm =>
            // this may be too specific for actually parsing dates
            // https://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.3
            // http://hc.apache.org/httpcomponents-client-ga/httpclient/apidocs/org/apache/http/client/utils/DateUtils.html
            Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(lm))
          }

          val content: Either[Array[Byte], String] = {
            if (ct.`type`() == "text" || ct.subtype() == "json") Right(response.body().string())
            else Left(response.body().bytes())
          }

          promise.success(VResponse(content, Vurl.fromString(location), ct.toString, lastModified, etag))

        }
        else promise.failure(RequestException(call.request().url().toString, s"${response.code}: ${response.message()}"))
      } finally response.close()
    })
    Log.Crawl.debug(s"returning ${okrequest.url()} in flight: ${client.dispatcher().queuedCallsCount()}/${client.dispatcher().runningCallsCount()}/${client.dispatcher().getMaxRequests()}/${client.dispatcher().getMaxRequestsPerHost()}")
    promise.future
  }
}
