package viscel.server

import better.files.File
import jakarta.servlet.http.{HttpServletRequest, HttpServletResponse}
import loci.communicator.ws.jetty.WS.Properties
import loci.communicator.ws.jetty._
import loci.registry.Registry
import org.eclipse.jetty.http.{HttpHeader, HttpMethod}
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
import viscel.store.{BlobStore, RowStoreV4, User}
import viscel.{FolderImporter, Viscel}

import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

class JettyServer(
    blobStore: BlobStore,
    terminate: () => Unit,
    pages: ServerPages,
    folderImporter: FolderImporter,
    interactions: Interactions,
    staticPath: File,
    urlPrefix: String,
    rowStore: RowStoreV4,
) {

  lazy val jettyServer: Server = {
    val threadPool = new QueuedThreadPool(4)
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

    override def handle(
        target: String,
        baseRequest: Request,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): Unit = {

      var credentials = request.getHeader(HttpHeader.AUTHORIZATION.asString());
      val charset     = StandardCharsets.ISO_8859_1;

      if (credentials != null) {
        val space = credentials.indexOf(' ')
        if (space > 0) {
          val method = credentials.substring(0, space)
          if ("basic".equalsIgnoreCase(method)) {
            credentials = credentials.substring(space + 1)
            credentials = new java.lang.String(Base64.getDecoder.decode(credentials), charset);
            val i = credentials.indexOf(':')
            if (i > 0) {
              val username = credentials.substring(0, i)
              val password = credentials.substring(i + 1)
              interactions.authenticate(username, password) match {
                case Some(user) =>
                  request.setAttribute("viscel-user", user)
                  return super.handle(target, baseRequest, request, response)
                case None =>
                  scribe.warn(s"incorrect login for $username")
              }

            }
          }
        }
      }

      scribe.info(s"no credetials for ${request.getRequestURI}")

      val value = "basic realm=\"viscel login\", charset=\"" + charset.name() + "\""
      response.setHeader(HttpHeader.WWW_AUTHENTICATE.asString(), value);
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED);

      baseRequest.setHandled(true)

    }

  }

  val staticResourceHandler = {
    // Create and configure a ResourceHandler.
    val handler = new ResourceHandler()
    // Configure the directory where static resources are located.
    handler.setBaseResource(Resource.newResource(staticPath.pathAsString))
    // Configure directory listing.
    handler.setDirectoriesListed(false)
    // Configure whether to accept range requests.
    handler.setAcceptRanges(true)
    handler
  }

  val blobsHandler = {

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
    //LociDist.distribute(handleBookmarks(userid), registry)(Bindings.bookmarksMapBindig)

    val context = new ServletContextHandler(ServletContextHandler.SESSIONS)
    context.setContextPath(urlPrefix)
    jettyServer.setHandler(context)

    registry.listen(WS(context, wspath, properties))
    context
  }

  trait Handling
  case class Res(content: String, ct: String = "text/html; charset=UTF-8", status: Int = 200) extends Handling
  case object Unhandled extends Handling

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
                  val fut = interactions.addNarratorsFrom(url).map(v => s"found $v")
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
          case other => Unhandled
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
