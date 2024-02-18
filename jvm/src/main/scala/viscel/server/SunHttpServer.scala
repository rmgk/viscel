package viscel.server

import com.sun.net.httpserver
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpHandlers, HttpServer, SimpleFileServer}
import org.eclipse.jetty.http.HttpHeader
import viscel.{FolderImporter, Viscel}
import viscel.store.{BlobStore, User}

import scala.jdk.CollectionConverters.given
import java.net.{HttpCookie, InetSocketAddress, URLConnection}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.Base64
import scala.annotation.unused
import scala.util.Using

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
  case object Unhandled                                                                       extends Handling

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
              if (isPost && user.admin) {
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

              } else exchange.getRequestURI.getPath match {
                case "/"        => Res(landingString)
                case "/version" => Res(Viscel.version, "text/plain; charset=UTF-8")
                case "/tools"   => Res(toolsString)
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
              case File(name) =>
                val ct = URLConnection.guessContentTypeFromName(name)
                println(s"guessed $name: $ct ")
                exchange.getResponseHeaders.add("Content-Type", ct)
                val body = Files.readAllBytes(staticPath.resolve(name))
                exchange.sendResponseHeaders(200, body.length)
                exchange.getResponseBody.write(body)
              case Unhandled =>
                exchange.sendResponseHeaders(404, 0)
            }
          }
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

}
