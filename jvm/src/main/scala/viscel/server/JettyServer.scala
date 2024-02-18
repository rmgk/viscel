package viscel.server

import loci.communicator.ws.jetty.*
import loci.communicator.ws.jetty.WS.Properties
import loci.registry.Registry
import org.eclipse.jetty.http.{HttpCookie, HttpHeader, HttpMethod, HttpStatus}
import org.eclipse.jetty.rewrite.handler.{RewriteHandler, RewriteRegexRule}
import org.eclipse.jetty.server.handler.{ContextHandler, ResourceHandler}
import org.eclipse.jetty.server.{Handler, Request, Response, Server, ServerConnector}
import org.eclipse.jetty.util.Callback
import org.eclipse.jetty.util.resource.ResourceFactory
import org.eclipse.jetty.util.thread.QueuedThreadPool
import org.eclipse.jetty.websocket.server.WebSocketUpgradeHandler
import rescala.default.Signal
import rescala.extra.distributables.LociDist
import viscel.shared.BookmarksMap.BookmarksMap
import viscel.shared.{Bindings, Vid}
import viscel.store.{BlobStore, User}
import viscel.{FolderImporter, Viscel}

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.Base64
import scala.annotation.unused
import scala.collection.mutable
import scala.concurrent.duration.*
import scala.concurrent.{Await, Promise}
import scala.jdk.CollectionConverters.*
import scala.util.chaining.scalaUtilChainingOps

class JettyServer(
    blobStore: BlobStore,
    terminate: () => Unit,
    pages: ServerPages,
    folderImporter: FolderImporter,
    interactions: Interactions,
    staticPath: Path,
    @unused urlPrefix: String,
) {

  lazy val jettyServer: Server = {
    val threadPool = new QueuedThreadPool().tap: p =>
      import p.*
      // we do not set max threads, as jetty may internally require a certain (unpredictable amount) and will complain if unavailable
      // instead, the connector below tries to limit concurrency with the number of acceptors and selectors
      setMinThreads(0)
      setIdleTimeout(10000 /*ms*/ )
      setName("http server")
    new Server(threadPool)
  }

  def stop(): Unit = jettyServer.stop()
  def start(interface: String, port: Int): Unit = {

    // connectors accept requests – in this case on a TCP socket
    // the acceptors/selectors value should cause this to have a single selector thread, which also handles the response
    val connector = new ServerConnector(jettyServer, 0, 1).tap: con =>
      import con.*
      setHost(interface)
      setPort(port)
    jettyServer.addConnector(connector)

    // val zip = new GzipHandler()
    // zip.addExcludedPaths("/blob/*")

    val lociHandler = lociWebsocketHandler()

    val seq = new Handler.Sequence(lociHandler, mainHandler, staticResourceHandler, blobsHandler)

    authenticationHandler.setHandler(seq)
    jettyServer.setHandler(authenticationHandler)

    jettyServer.start()
  }

  object authenticationHandler extends Handler.Wrapper {

    def loginFromCredentials(credentials: String): Option[(String, String)] = {
      if (credentials != null) {
        val space = credentials.indexOf(' ')
        if (space > 0) {
          val method = credentials.substring(0, space)
          if ("basic".equalsIgnoreCase(method)) {
            val credentials2 = credentials.substring(space + 1)
            val credentials3 = new String(Base64.getDecoder.decode(credentials2), StandardCharsets.ISO_8859_1);
            val i            = credentials3.indexOf(':')
            if (i > 0) {
              val username = credentials3.substring(0, i)
              val password = credentials3.substring(i + 1)
              return Some(username -> password)
            }
          }
        }
      }
      None
    }

    override def handle(
        request: Request,
        response: Response,
        callback: Callback,
    ): Boolean = {

      val credentials              = request.getHeaders.get(HttpHeader.AUTHORIZATION)
      val cookies: Seq[HttpCookie] = Option(Request.getCookies(request)).map(_.asScala.toSeq).getOrElse(Nil)

      def getCookie(name: String) = {
        cookies.find(c => c.getName == name).map(_.getValue)
      }

      loginFromCredentials(credentials).orElse {
        getCookie("viscel-user").zip(getCookie("viscel-password"))
      } flatMap { case (username, password) =>
        interactions.authenticate(username, password)
      } match {
        case Some(user) =>
          request.setAttribute("viscel-user", user)
          val twelveMonths: Long = 12 * 30 * 24 * 60
          val userCookie = HttpCookie.build("viscel-user", user.id).maxAge(twelveMonths)
            .sameSite(HttpCookie.SameSite.STRICT).build()
          val passCookie = HttpCookie.build("viscel-password", user.password).maxAge(twelveMonths)
            .sameSite(HttpCookie.SameSite.STRICT).build()
          Response.addCookie(response, userCookie)
          Response.addCookie(response, passCookie)
          super.handle(request, response, callback)
        case None =>
          scribe.info(s"no credetials for ${request.getHttpURI}")
          // scribe.info(s"cookie header: ${request.getHeader("Cookie")}")
          // cookies.foreach { c =>
          //  scribe.info(s"cookie: ${c.getName}: ${c.getValue}")
          // }
          // scribe.info(s"auth: ${request.getHeader("Authorization")}")

          val value = "basic realm=\"viscel login\", charset=\"" + StandardCharsets.ISO_8859_1.name() + "\""
          response.getHeaders.add(HttpHeader.WWW_AUTHENTICATE, value)
          Response.writeError(request, response, callback, HttpStatus.UNAUTHORIZED_401)
          callback.succeeded()
          true
      }

    }

  }

  val staticResourceHandler = {
    // Create and configure a ResourceHandler.
    val handler = new ResourceHandler()
    // Configure the directory where static resources are located.
    handler.setBaseResource(ResourceFactory.of(handler).newResource(staticPath))
    // Configure directory listing.
    handler.setDirAllowed(false)
    // Configure whether to accept range requests.
    handler.setAcceptRanges(true)
    handler
  }

  lazy val blobsHandler = {

    val resourceHandler = new ResourceHandler()
    resourceHandler.setBaseResource(ResourceFactory.of(resourceHandler).newResource(blobStore.blobdir))
    resourceHandler.setCacheControl("max-age=31557600, public, immutable")

    val rewriteHandler = new RewriteHandler()
    rewriteHandler.addRule(new RewriteRegexRule("/blob/(..)(.*)", "/$1/$2"))
    rewriteHandler.setHandler(resourceHandler)

    val contentTypeHandler = new Handler.Wrapper {
      override def handle(
          request: Request,
          response: Response,
          callback: Callback,
      ): Boolean = {

        if !request.getHttpURI.getPath.startsWith("/blob/")
        then false
        else
          val ct: String =
            Option(Request.extractQueryParameters(request).get("mime")).map(_.getValue).getOrElse("image")
          response.getHeaders.add(HttpHeader.CONTENT_TYPE, ct)
          super.handle(request, response, callback)
      }
    }

    contentTypeHandler.setHandler(rewriteHandler)
    contentTypeHandler
  }

  val landingString: String = pages.fullrender(pages.landingTag)
  val toolsString: String   = pages.fullrender(pages.toolsPage)

  def lociWebsocketHandler() = {

    val wspath     = "/ws"
    val properties = Properties(heartbeatDelay = 3.seconds, heartbeatTimeout = 10.seconds)

    val registry = new Registry
    interactions.bindGlobalData(registry)

    val userSocketCache: mutable.Map[User.Id, Signal[BookmarksMap]] = mutable.Map.empty

    LociDist.distributePerRemote(
      { rr =>
        val user =
          rr.protocol.asInstanceOf[loci.communicator.ws.jetty.WS].request.get.getAttribute(
            "viscel-user"
          ).asInstanceOf[User]
        userSocketCache.getOrElseUpdate(user.id, interactions.handleBookmarks(user.id))
      },
      registry
    )(Bindings.bookmarksMapBindig)
    // LociDist.distribute(handleBookmarks(userid), registry)(Bindings.bookmarksMapBindig)

    val contextHandler   = new ContextHandler()
    val webSocketHandler = WebSocketUpgradeHandler.from(jettyServer, contextHandler)
    registry.listen(WS(webSocketHandler, wspath, properties))
    contextHandler.setHandler(webSocketHandler)
    contextHandler
  }

  sealed trait Handling
  case class Res(content: String, ct: String = "text/html; charset=UTF-8", status: Int = 200) extends Handling
  case object Unhandled                                                                       extends Handling

  object mainHandler extends Handler.Abstract {
    override def handle(
        request: Request,
        response: Response,
        callback: Callback,
    ): Boolean = {

      def isAdmin = request.getAttribute("viscel-user").asInstanceOf[User].admin
      def isPost  = HttpMethod.POST.is(request.getMethod)

      val res = {
        if (isPost && isAdmin) {
          request.getHttpURI.getPath match {
            case "/stop" =>
              terminate()
              Res("")
            case "/import" =>
              val params = Request.extractQueryParameters(request)
              List("id", "name", "path").flatMap(key => Option(params.get(key)).map(_.getValue)) match {
                case List(id, name, path) =>
                  folderImporter.importFolder(path, Vid.from(s"Import_$id"), name)
                  Res("success")
                case _ =>
                  Res("invalid parameters", status = 500)
              }
            case "/add" =>
              val params = Request.extractQueryParameters(request)

              List("url").flatMap(key => Option(params.get(key)).map(_.getValue)) match {
                case List(url) =>
                  val fut = interactions.addNarratorsFrom(url).map(v => s"found $v").runToFuture(using ())
                  Res(Await.result(fut, 60.seconds))
                case _ =>
                  Res("invalid parameters", status = 500)
              }
            case other => Unhandled
          }

        } else request.getHttpURI.getPath match {
          case "/"        => Res(landingString)
          case "/version" => Res(Viscel.version, "text/plain; charset=UTF-8")
          case "/tools"   => Res(toolsString)
          case other      => Unhandled
        }
      }

      res match {
        case Res(str, ct, status) =>
          response.setStatus(status)
          response.getHeaders.put(HttpHeader.CONTENT_TYPE, ct)
          response.write(true, StandardCharsets.UTF_8.encode(str), callback)
          true
        case Unhandled =>
          false
      }
    }

  }

}
