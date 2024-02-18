package viscel.server

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonValueCodec, readFromArray, writeToArray}
import com.sun.net.httpserver
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpHandlers, HttpServer, SimpleFileServer}
import viscel.shared.BookmarksMap.BookmarksMap
import viscel.shared.{Log, Vid}
import viscel.{FolderImporter, Viscel}
import viscel.store.{BlobStore, User}

import scala.jdk.CollectionConverters.given
import java.net.{HttpCookie, InetSocketAddress, URLConnection}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.Base64
import scala.annotation.unused
import scala.util.{Try, Using}
import scala.util.chaining.scalaUtilChainingOps
import viscel.shared.JsoniterCodecs.*

import java.io.ByteArrayInputStream

class SunHttpServer(
    blobStore: BlobStore,
    terminate: () => Unit,
    pages: ServerPages,
    folderImporter: FolderImporter,
    interactions: Interactions,
    staticPath: Path,
    urlPrefix: String,
) {

  sealed trait Handling
  case class Res(content: String, ct: String = "text/html; charset=UTF-8", status: Int = 200) extends Handling
  case class File(str: String)                                                                extends Handling
  case class Blob(str: Path, ct: Option[String])                                              extends Handling
  case object Unhandled                                                                       extends Handling
  case class Json[T: JsonValueCodec](value: T) extends Handling {
    def encode(): Array[Byte] = writeToArray(value)
  }

  val server = HttpServer.create()

  def stop(): Unit = server.stop(3)
  def start(interface: String, port: Int): Unit = {

    server.bind(InetSocketAddress(interface, port), 0)

    val staticFiles = Using(Files.list(staticPath))(_.toList.asScala).getOrElse(Iterable.empty).iterator.map: path =>
      s"/${path.getFileName.toString}"
    .toSet

    server.createContext(
      s"$urlPrefix/",
      (exchange: HttpExchange) => {

        authenticationHandler.handle(exchange) match
          case None =>
          case Some(user) => {

            val isPost = exchange.getRequestMethod == "POST"

            val res = {
              val adminres = if (isPost && user.admin) {
                exchange.getRequestURI.getPath match {
                  case "/stop" =>
                    terminate()
                    Res("")
                  case "/import" =>
                    ???
                  //                      val params = exchange.getRequestURI.getQuery
                  //                      List("id", "name", "path").flatMap(key => Option(params.get(key)).map(_.getValue)) match {
                  //                        case List(id, name, path) =>
                  //                          folderImporter.importFolder(path, Vid.from(s"Import_$id"), name)
                  //                          Res("success")
                  //                        case _ =>
                  //                          Res("invalid parameters", status = 500)
                  //                      }
                  case "/add" =>
                    ???
                  //                      val params = Request.extractQueryParameters(request)
                  //
                  //                      List("url").flatMap(key => Option(params.get(key)).map(_.getValue)) match {
                  //                        case List(url) =>
                  //                          val fut = interactions.addNarratorsFrom(url).map(v => s"found $v").runToFuture(using ())
                  //                          Res(Await.result(fut, 60.seconds))
                  //                        case _ =>
                  //                          Res("invalid parameters", status = 500)
                  //                      }
                  case other => Unhandled
                }

              } else Unhandled
              if adminres != Unhandled then adminres
              else
                exchange.getRequestURI.getPath match {
                  case "/"             => Res(landingString)
                  case "/version"      => Json(Viscel.version)
                  case "/tools"        => Res(toolsString)
                  case "/descriptions" => Json(interactions.contentLoader.descriptions())
                  case other if other.startsWith("/blob/") =>
                    val hash = other.substring(6)
                    val path = blobStore.hashToPath(hash)
                    if Files.exists(path) then
                      Blob(path, Try(exchange.getRequestURI.getQuery.substring(5)).toOption)
                    else Unhandled
                  case "/bookmarksmap" if isPost =>
                    val body = exchange.getRequestBody.readAllBytes()
                    val bm   = readFromArray[BookmarksMap](body)
                    Json(interactions.handleBookmarks(user.id, bm))
                  case other if other.startsWith("/contents/") =>
                    Json(interactions.contentLoader.contents(Vid.from(other.substring(10))))
                  case other if other.startsWith("/hint/") =>
                    println(s"todo force")
                    interactions.handleHint(Vid.from(other.substring(6)), false)
                    Res("")
                  case other if staticFiles.contains(other) =>
                    File(other.substring(1))
                }
            }

            res match {
              case Res(str, ct, status) =>
                exchange.getResponseHeaders.add("Content-Type", ct)
                val body = str.getBytes(StandardCharsets.UTF_8)
                exchange.sendResponseHeaders(status, body.length)
                exchange.getResponseBody.write(body)
              case Blob(path, ctm) =>
                val body = Files.readAllBytes(path)
                ctm.orElse(Option(URLConnection.guessContentTypeFromStream(ByteArrayInputStream(body)))).foreach(ct =>
                  exchange.getResponseHeaders.add("Content-Type", ct)
                )
                exchange.sendResponseHeaders(200, body.length)
                exchange.getResponseBody.write(body)
              case File(name) =>
                guessContentType(name).foreach: ct =>
                  exchange.getResponseHeaders.add("Content-Type", ct)
                val body = Files.readAllBytes(staticPath.resolve(name))
                exchange.sendResponseHeaders(200, body.length)
                exchange.getResponseBody.write(body)
              case json: Json[?] =>
                exchange.getResponseHeaders.add("Content-type", "application/json;charset=utf-8")
                val body = json.encode()
                exchange.sendResponseHeaders(200, body.length)
                exchange.getResponseBody.write(body)
              case Unhandled =>
                println(s"unhandled request for ${exchange.getRequestMethod} ${exchange.getRequestURI}")
                exchange.sendResponseHeaders(404, 0)
            }
          }
        exchange.close()
      }
    )

    server.start()
    ()

  }

  val landingString: String = pages.fullrender(pages.landingTag)
  val toolsString: String   = pages.fullrender(pages.toolsPage)

  object authenticationHandler {

    def loginFromCredentials(credentials: Option[String]): Option[(String, String)] =
      credentials match
        case None => None
        case Some(credentials) =>
          val space = credentials.indexOf(' ')
          if space > 0 then
            val method = credentials.substring(0, space)
            if "basic".equalsIgnoreCase(method) then
              val credentials2 = credentials.substring(space + 1)
              val credentials3 = new String(Base64.getDecoder.decode(credentials2), StandardCharsets.ISO_8859_1);
              val i            = credentials3.indexOf(':')
              if i > 0 then
                val username = credentials3.substring(0, i)
                val password = credentials3.substring(i + 1)
                return Some(username -> password)
          None

    def handle(
        exchange: HttpExchange
    ): Option[User] = {

      val credentials = Option(exchange.getRequestHeaders.getFirst("Authorization"))
      val cookies = Option(exchange.getRequestHeaders.getFirst("Cookie")).map: str =>
        HttpCookie.parse(str).asScala.map: cookie =>
          (cookie.getName, cookie.getValue)
        .toMap
      .getOrElse(Map.empty)

      loginFromCredentials(credentials).orElse {
        cookies.get("viscel-user").zip(cookies.get("viscel-password"))
      } flatMap { case (username, password) =>
        interactions.authenticate(username, password)
      } match {
        case Some(user) =>
          exchange.setAttribute("viscel-user", user)
          val twelveMonths: Long = 12 * 30 * 24 * 60 * 60
          exchange.getResponseHeaders.add(
            "set-cookie",
            s"viscel-user=${user.id}; Max-Age=$twelveMonths; SameSite=Strict"
          )
          exchange.getResponseHeaders.add(
            "set-cookie",
            s"viscel-password=${user.password}; Max-Age=$twelveMonths; SameSite=Strict"
          )
          Some(user)

        case None =>
          scribe.info(s"no credetials for ${exchange.getRequestURI}")
          // scribe.info(s"cookie header: ${request.getHeader("Cookie")}")
          // cookies.foreach { c =>
          //  scribe.info(s"cookie: ${c.getName}: ${c.getValue}")
          // }
          // scribe.info(s"auth: ${request.getHeader("Authorization")}")

          val value = "basic realm=\"viscel login\", charset=\"" + StandardCharsets.ISO_8859_1.name() + "\""
          exchange.getResponseHeaders.add("WWW-Authenticate", value)
          exchange.sendResponseHeaders(401, 0)
          None
      }

    }

  }

  val contentType = Map(
    ("json", "application/json;charset=utf-8"),
    ("js", "text/javascript;charset=utf-8"),
    ("js.map", "application/json;charset=utf-8"),
    ("css", "text/css;charset=utf-8"),
    ("svg", "image/svg+xml"),
    ("png", "image/png"),
    ("min.js", "text/javascript;charset=utf-8"),
  )

  def guessContentType(str: String) =
    val index = str.indexOf(".")
    if index < 0 then
      Log.Server.info(s"unknown mime type for $str")
      None
    else
      val ending = str.substring(index + 1)
      contentType.get(ending)

}
