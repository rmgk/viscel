package viscel.netzi

import de.rmgk.delay.{Async, syntax}
import viscel.crawl.RequestException
import viscel.shared.{Log, Vurl}

import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.net.{CookieManager, HttpCookie, URI, URL}
import java.nio.charset.{Charset, StandardCharsets}
import java.time.format.DateTimeFormatter
import java.time.{Duration, Instant}
import java.util.concurrent.{CompletionException, ExecutorService}
import scala.annotation.nowarn
import scala.jdk.OptionConverters.RichOptional

class JvmHttpRequester(
    val executorService: ExecutorService,
    cookies: Map[String, (String, String)]
) extends WebRequestInterface {

  val timeout = Duration.ofSeconds(60)

  val cookieManager =
    val cm = new CookieManager()
    cookies.foreach { case (uristring, (name, value)) =>
      val uri    = new URI(uristring)
      val cookie = new HttpCookie(name, value)
      cm.getCookieStore.add(uri, cookie)
    }
    cm

  val client: HttpClient = HttpClient.newBuilder()
    .connectTimeout(timeout)
    .executor(executorService)
    .followRedirects(HttpClient.Redirect.NORMAL)
    .cookieHandler(cookieManager)
    .build()

  val referrer = "Referer"

  def urlUri(urlstring: String): URI = {
    val url = new URL(urlstring): @nowarn
    new URI(url.getProtocol, url.getUserInfo, url.getHost, url.getPort, url.getPath, url.getQuery, url.getRef)
  }

  def vreqToHttpRequest(vrequest: VRequest): HttpRequest = {
    val uri = urlUri(vrequest.href.uriString())
    val base = HttpRequest.newBuilder().uri(uri).timeout(timeout)
      .setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:68.0) Gecko/20100101 Firefox/68.0")
    val res = vrequest.referer match
      case None      => base
      case Some(ref) => base.setHeader(referrer, ref.uriString())
    res.build()
  }

  override def get(request: VRequest): Async[Any, VResponse[Either[Array[Byte], String]]] = {
    val hreq = vreqToHttpRequest(request)
    Log.Crawl.info(s"request ${hreq.uri()}${hreq.headers.firstValue(referrer).toScala.fold("")(r => s" ($r)")}")
    val exec = Async[Int] {
      val response: HttpResponse[Array[Byte]] = client.sendAsync(hreq, BodyHandlers.ofByteArray()).toAsync.bind
      if (response.statusCode() != 200)
        throw RequestException(response.uri().toString, s"${response.statusCode()}")
      val contentType: String = response.headers().firstValue("content-type").toScala.getOrElse("")
      val location            = response.uri().toString
      val etag                = response.headers.firstValue("ETag").toScala
      val lastModified = response.headers.firstValue("Last-Modified").toScala.map { lm =>
        // this may be too specific for actually parsing dates
        // https://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.3
        // http://hc.apache.org/httpcomponents-client-ga/httpclient/apidocs/org/apache/http/client/utils/DateUtils.html
        Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(lm))
      }

      val content: Either[Array[Byte], String] = {
        if !(contentType.contains("text") || contentType.contains("json"))
        then Left(response.body())
        else
          val CharsetRegex = """charset=(\S+)""".r
          val charset = contentType match
            case CharsetRegex(charsetname) => Charset.forName(charsetname)
            case other                     => StandardCharsets.UTF_8
          Right(new String(response.body(), charset))

      }

      VResponse(content, Vurl.fromString(location), contentType.toString, lastModified, etag)
    }
    lazy val rec: Async[Int, VResponse[Either[Array[Byte], String]]] = exec.recover {
      case e: CompletionException if e.getMessage.contains("GOAWAY") && summon[Int] > 0 =>
        rec.provide(summon[Int] - 1)
      case other => throw other
    }
    rec.provide(2)
  }
}
