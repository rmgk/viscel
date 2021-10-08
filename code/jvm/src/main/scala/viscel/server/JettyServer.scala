package viscel.server

import better.files.File
import jakarta.servlet.ServletRequest
import jakarta.servlet.http.{HttpServletRequest, HttpServletResponse}
import loci.communicator.ws.jetty.WS.Properties
import loci.communicator.ws.jetty._
import loci.registry.Registry
import org.eclipse.jetty.http.HttpHeader
import org.eclipse.jetty.rewrite.handler.{RewriteHandler, RewriteRegexRule}
import org.eclipse.jetty.security.authentication.BasicAuthenticator
import org.eclipse.jetty.security.{
  ConstraintMapping, ConstraintSecurityHandler, DefaultIdentityService, IdentityService, LoginService
}
import org.eclipse.jetty.server.handler.gzip.GzipHandler
import org.eclipse.jetty.server.handler.{AbstractHandler, HandlerList, HandlerWrapper, ResourceHandler}
import org.eclipse.jetty.server.{Request, Server, ServerConnector, UserIdentity}
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.util.resource.Resource
import org.eclipse.jetty.util.security.Constraint
import org.eclipse.jetty.util.thread.QueuedThreadPool
import rescala.default.Signal
import rescala.extra.distributables.LociDist
import viscel.FolderImporter
import viscel.shared.Bindings
import viscel.shared.BookmarksMap.BookmarksMap
import viscel.store.{BlobStore, RowStoreV4, User}

import java.nio.charset.StandardCharsets
import java.security.Principal
import java.util.Base64
import javax.security.auth.Subject
import scala.collection.mutable
import scala.concurrent.duration._

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

  lazy val jettyServer = {
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

  val securityHandler = {
    val security = new ConstraintSecurityHandler()
    val auth     = new BasicAuthenticator()
    security.setAuthenticator(auth)
    val login = new LoginService {
      override def getName: String = "viscel"
      override def login(username: String, credentials: Any, request: ServletRequest): UserIdentity = {
        println(s"login $username, $credentials")
        val res = interactions.authenticate(username, credentials.toString)
        println(s"res is $res")
        res.map { user =>
          val principal = new Principal {
            override def getName: String = user.id
          }
          new UserIdentity {
            override def getSubject: Subject = {
              val s = new Subject()
              s.getPrincipals.add(principal)
              s
            }
            override def getUserPrincipal: Principal                                    = principal
            override def isUserInRole(role: String, scope: UserIdentity.Scope): Boolean = true
          }
        }
      }.orNull
      override def validate(user: UserIdentity): Boolean              = true
      override def getIdentityService: IdentityService                = null
      override def setIdentityService(service: IdentityService): Unit = ()
      override def logout(user: UserIdentity): Unit                   = ()
    }
    security.setLoginService(login)
    security.setIdentityService(new DefaultIdentityService())
    val constraint = new Constraint()
    constraint.setName("auth")
    constraint.setAuthenticate(true)
    val mapping = new ConstraintMapping();
    mapping.setPathSpec("/*");
    mapping.setConstraint(constraint);
    security.setConstraintMappings(Array(mapping))
    security
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

              println(s"authorizing $username $password")
              interactions.authenticate(username, password) match {
                case Some(user) =>
                  request.setAttribute("viscel-user", user)
                  return super.handle(target, baseRequest, request, response)
                case None =>
                  scribe.info(s"incorrect login for $username")
              }

            }
          }
        }
      }

      println(s"no credetials for ${request.getRequestURI}")

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
    val rewriteHandler = new RewriteHandler();
    // Rewrites */products/* to */p/*.
    rewriteHandler.addRule(new RewriteRegexRule("/blob/(..)(.*)", "/$1/$2"))
    val handler = new ResourceHandler()
    val blobdir = blobStore.blobdir.toString
    handler.setBaseResource(Resource.newResource(blobdir))
    handler.setCacheControl("max-age=31557600, public, immutable")
    rewriteHandler.setHandler(handler)
    rewriteHandler
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
        println(s"accessing user")
        val user =
          rr.protocol.asInstanceOf[loci.communicator.ws.jetty.WS].request.get.getHttpServletRequest.getAttribute(
            "viscel-user"
          ).asInstanceOf[User]
        println(s"user is ${user.id}")
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

  object mainHandler extends AbstractHandler {
    override def handle(
        target: String,
        baseRequest: Request,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): Unit = {
      println(s"request $target")
      if (request.getRequestURI == "/") {
        response.setStatus(200)
        response.setContentType("text/html; charset=UTF-8")
        response.getWriter.println(landingString)
        baseRequest.setHandled(true)
      }
    }
  }

  //def setup(): Javalin = {
  //  jl.get(
  //    "version",
  //    ctx => {
  //      ctx.contentType("text/plain")
  //      ctx.result(Viscel.version)
  //    }
  //  )
  //  jl.get(
  //    "",
  //    { ctx =>
  //      ctx.result(landingString)
  //    }
  //  )
  //  jl.get(
  //    "blob/:hash",
  //    { ctx =>
  //      val sha1      = ctx.pathParamMap.get("hash")
  //      val filename  = File(blobStore.hashToPath(sha1))
  //      val mediatype = Option(ctx.queryParamMap.get("mime")).flatMap(_.asScala.headOption).getOrElse("image")
  //      ctx.contentType(mediatype)
  //      if (filename.exists) {
  //        ctx.header(Header.CACHE_CONTROL, "max-age=31557600, public, immutable")
  //        ctx.result(filename.newInputStream)
  //      } else {
  //        ctx.status(404)
  //        ctx.result("")
  //      }
  //    }
  //  )
  //  jl.post(
  //    "stop",
  //    { ctx =>
  //      if (ctx.attribute[User]("user").admin) {
  //        terminate()
  //      }
  //      ctx.result("")
  //    }
  //  )
  //  jl.get(
  //    "tools",
  //    { ctx =>
  //      ctx.result(toolsString)
  //    }
  //  )
  //  jl.post(
  //    "import",
  //    { ctx =>
  //      if (ctx.attribute[User]("user").admin) {
  //        val params = ctx.formParamMap().asScala
  //        List("id", "name", "path").flatMap(key => params.get(key).flatMap(_.asScala.headOption)) match {
  //          case List(id, name, path) =>
  //            import scala.concurrent.ExecutionContext.Implicits.global
  //            ctx.result(Future(folderImporter.importFolder(path, Vid.from(s"Import_$id"), name))
  //              .map(_ => "success").asJava.toCompletableFuture)
  //          case _ =>
  //            ctx.status(500)
  //            ctx.result("")
  //        }
  //      } else {
  //        ctx.status(403)
  //        ctx.result("")
  //      }
  //    }
  //  )
  //  jl.post(
  //    "add",
  //    { ctx =>
  //      if (ctx.attribute[User]("user").admin) {
  //        val params = ctx.formParamMap().asScala
  //        List("url").flatMap(key => params.get(key).flatMap(_.asScala.headOption)) match {
  //          case List(url) =>
  //            import scala.concurrent.ExecutionContext.Implicits.global
  //            ctx.result(interactions.addNarratorsFrom(url)
  //              .map(v => s"found $v")
  //              .asJava.toCompletableFuture)
  //          case _ =>
  //            ctx.status(500)
  //            ctx.result("")
  //        }
  //      } else {
  //        ctx.status(403)
  //        ctx.result("")
  //      }
  //    }
  //  )
  //  jl.get(
  //    "db4/:vid",
  //    { ctx =>
  //      val vid   = Vid.from(ctx.pathParam("vid"))
  //      val bytes = rowStore.file(vid).byteArray
  //      ctx.status(200)
  //      ctx.result(new ByteArrayInputStream(bytes))
  //      ctx.contentType("text/plain; charset=UTF-8")
  //    }
  //  )
  //  jl.get(
  //    "dots/:vid",
  //    { ctx =>
  //      val vid       = Vid.from(ctx.pathParam("vid"))
  //      val (_, rows) = rowStore.load(vid)
  //      val edges = rows.map {
  //        case DataRow(ref, loc, lastModified, etag, contents) =>
  //          val cs = contents.flatMap {
  //            case DataRow.Link(ref, data) => Some("\"" + ref.uriString().toString + "\"")
  //            case other                   => None
  //          }
  //          s""""${ref.uriString()}" -> {${cs.mkString("; ")}};"""
  //      }.mkString("\n")
  //      ctx.result(s"digraph { \n$edges}\n")
  //      ctx.contentType("text/plain; charset=UTF-8")
  //      ctx.status(200)
  //    }
  //  )
  //}
}
