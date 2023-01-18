package viscel.server

import jakarta.servlet.http.{Cookie, HttpServletRequest, HttpServletResponse}
import loci.communicator.ws.jetty.*
import loci.communicator.ws.jetty.WS.Properties
import loci.registry.Registry
import org.eclipse.jetty.http.{HttpCookie, HttpHeader, HttpMethod}
import org.eclipse.jetty.rewrite.handler.{RewriteHandler, RewriteRegexRule}
import org.eclipse.jetty.server.handler.gzip.GzipHandler
import org.eclipse.jetty.server.handler.{AbstractHandler, HandlerList, HandlerWrapper, ResourceHandler}
import org.eclipse.jetty.server.{Request, Server, ServerConnector}
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.util.resource.Resource
import org.eclipse.jetty.util.thread.QueuedThreadPool
import rescala.default.Signal
import rescala.extra.distributables.LociDist
import viscel.shared.BookmarksMap.BookmarksMap
import viscel.shared.{Bindings, Vid}
import viscel.store.{BlobStore, User}
import viscel.{FolderImporter, Viscel}

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.Base64
import scala.collection.mutable
import scala.concurrent.duration.*
import scala.concurrent.{Await, Promise}
import scala.jdk.CollectionConverters.*

class JettyServer(
    blobStore: BlobStore,
    terminate: () => Unit,
    pages: ServerPages,
    folderImporter: FolderImporter,
    interactions: Interactions,
    staticPath: Path,
    urlPrefix: String,
) {

  lazy val jettyServer: Server = {
    val threadPool = new QueuedThreadPool(4, 1)
    threadPool.setName("http server")
    new Server(threadPool)
  }

  def stop(): Unit = jettyServer.stop()
  def start(interface: String, port: Int): Unit = {

    // connectors accept requests – in this case on a TCP socket
    val connector = new ServerConnector(jettyServer)
    jettyServer.addConnector(connector)
    connector.setHost(interface)
    connector.setPort(port)

    val zip = new GzipHandler()
    zip.addExcludedPaths("/blob/*")
    zip.setHandler(new HandlerList(mainHandler, staticResourceHandler, blobsHandler, wsSetup()))

    authenticationHandler.setHandler(zip)
    jettyServer.setHandler(authenticationHandler)

    jettyServer.start()
  }

  object authenticationHandler extends HandlerWrapper {

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
        target: String,
        baseRequest: Request,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): Unit = {

      val credentials = request.getHeader(HttpHeader.AUTHORIZATION.asString())
      val cookies     = Option(request.getCookies).getOrElse(Array[Cookie]())

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
          val userCookie = new HttpCookie(
            "viscel-user",
            user.id,
            null,
            null,
            twelveMonths,
            false,
            false,
            null,
            -1,
            HttpCookie.SameSite.STRICT
          )
          val passCookie = new HttpCookie(
            "viscel-password",
            user.password,
            null,
            null,
            twelveMonths,
            false,
            false,
            null,
            -1,
            HttpCookie.SameSite.STRICT
          )
          response.addHeader("Set-Cookie", userCookie.getRFC6265SetCookie)
          response.addHeader("Set-Cookie", passCookie.getRFC6265SetCookie)
          super.handle(target, baseRequest, request, response)
        case None =>
          scribe.info(s"no credetials for ${request.getRequestURI}")
          // scribe.info(s"cookie header: ${request.getHeader("Cookie")}")
          // cookies.foreach { c =>
          //  scribe.info(s"cookie: ${c.getName}: ${c.getValue}")
          // }
          // scribe.info(s"auth: ${request.getHeader("Authorization")}")

          val value = "basic realm=\"viscel login\", charset=\"" + StandardCharsets.ISO_8859_1.name() + "\""
          response.addHeader(HttpHeader.WWW_AUTHENTICATE.asString(), value);
          response.sendError(HttpServletResponse.SC_UNAUTHORIZED);

          baseRequest.setHandled(true)
      }

    }

  }

  val staticResourceHandler = {
    // Create and configure a ResourceHandler.
    val handler = new ResourceHandler()
    // Configure the directory where static resources are located.
    handler.setBaseResource(Resource.newResource(staticPath.toString))
    // Configure directory listing.
    handler.setDirectoriesListed(false)
    // Configure whether to accept range requests.
    handler.setAcceptRanges(true)
    handler
  }

  lazy val blobsHandler = {

    val resourceHandler = new ResourceHandler()
    val blobdir         = blobStore.blobdir.toString
    resourceHandler.setBaseResource(Resource.newResource(blobdir))
    resourceHandler.setCacheControl("max-age=31557600, public, immutable")

    val rewriteHandler = new RewriteHandler();
    rewriteHandler.addRule(new RewriteRegexRule("/blob/(..)(.*)", "/$1/$2"))
    rewriteHandler.setHandler(resourceHandler)

    val contentTypeHandler = new HandlerWrapper {
      override def handle(
          target: String,
          baseRequest: Request,
          request: HttpServletRequest,
          response: HttpServletResponse
      ): Unit = {
        val ct = Option(request.getParameter("mime")).getOrElse("image")
        response.setContentType(ct)
        super.handle(target, baseRequest, request, response)
      }
    }

    contentTypeHandler.setHandler(rewriteHandler)
    contentTypeHandler
  }

  val landingString: String = pages.fullrender(pages.landingTag)
  val toolsString: String   = pages.fullrender(pages.toolsPage)

  def wsSetup() = {

    val wspath     = "/ws"
    val properties = Properties(heartbeatDelay = 3.seconds, heartbeatTimeout = 10.seconds)

    val registry = new Registry
    interactions.bindGlobalData(registry)

    val userSocketCache: mutable.Map[User.Id, Signal[BookmarksMap]] = mutable.Map.empty

    LociDist.distributePerRemote(
      { rr =>
        val user =
          rr.protocol.asInstanceOf[loci.communicator.ws.jetty.WS].request.get.getHttpServletRequest.getAttribute(
            "viscel-user"
          ).asInstanceOf[User]
        userSocketCache.getOrElseUpdate(user.id, interactions.handleBookmarks(user.id))
      },
      registry
    )(Bindings.bookmarksMapBindig)
    // LociDist.distribute(handleBookmarks(userid), registry)(Bindings.bookmarksMapBindig)

    val context = new ServletContextHandler(ServletContextHandler.SESSIONS)
    if !urlPrefix.isBlank then context.setContextPath(urlPrefix)
    jettyServer.setHandler(context)

    registry.listen(WS(context, wspath, properties))
    context
  }

  sealed trait Handling derives CanEqual
  case class Res(content: String, ct: String = "text/html; charset=UTF-8", status: Int = 200) extends Handling
  case object Unhandled                                                                       extends Handling

  object mainHandler extends AbstractHandler {
    override def handle(
        target: String,
        baseRequest: Request,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): Unit = {

      def isAdmin = request.getAttribute("viscel-user").asInstanceOf[User].admin
      def isPost  = HttpMethod.POST.is(request.getMethod)

      val res = {
        if (isPost && isAdmin) {
          request.getRequestURI match {
            case "/stop" =>
              terminate()
              Res("")
            case "/import" =>
              val params = request.getParameterMap.asScala
              List("id", "name", "path").flatMap(key => params.get(key).flatMap(_.headOption)) match {
                case List(id, name, path) =>
                  folderImporter.importFolder(path, Vid.from(s"Import_$id"), name)
                  Res("success")
                case _ =>
                  Res("invalid parameters", status = 500)
              }
            case "/add" =>
              val params = request.getParameterMap.asScala

              List("url").flatMap(key => params.get(key).flatMap(_.headOption)) match {
                case List(url) =>
                  import scala.concurrent.ExecutionContext.Implicits.global
                  val fut = interactions.addNarratorsFrom(url).map(v => s"found $v").runToFuture(using ())
                  Res(Await.result(fut, 60.seconds))
                case _ =>
                  Res("invalid parameters", status = 500)
              }
            case other => Unhandled
          }

        } else request.getRequestURI match {
          case "/"        => Res(landingString)
          case "/version" => Res(Viscel.version, "text/plain; charset=UTF-8")
          case "/tools"   => Res(toolsString)
          case other      => Unhandled
        }
      }

      res match {
        case Res(str, ct, status) =>
          response.setStatus(status)
          response.setContentType(ct)
          response.getWriter.println(str)
          baseRequest.setHandled(true)
        case Unhandled =>
      }
    }

  }

}
