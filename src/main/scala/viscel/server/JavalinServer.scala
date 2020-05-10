package viscel.server


import java.util.function.Consumer

import better.files._
import io.javalin.Javalin
import io.javalin.core.compression.{Brotli, Gzip}
import io.javalin.core.util.Header
import io.javalin.http.staticfiles.Location
import io.javalin.websocket.WsHandler
import loci.communicator.ws.javalin.WS.Properties
import loci.communicator.{Listener, Listening}
import loci.registry.Registry
import rescala.default.Signal
import rescala.extra.distributables.LociDist
import viscel.shared.BookmarksMap.BookmarksMap
import viscel.shared.{Bindings, Vid}
import viscel.store.{BlobStore, User}
import viscel.{FolderImporter, Viscel}

import scala.annotation.nowarn
import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._
import scala.util.{Success, Try}


class JavalinServer(blobStore: BlobStore,
                    terminate: () => Unit,
                    pages: ServerPages,
                    folderImporter: FolderImporter,
                    interactions: Interactions,
                    staticPath: File,
                    urlPrefix: String
                   ) {

  def stop() = jl.stop()
  def start(interface: String, port: Int) = {
    setup()
    wsSetup()
    jl.start(interface, port)
  }


  lazy val jl: Javalin = Javalin.create { config =>
    config.contextPath = urlPrefix
    config.defaultContentType = "text/html; charset=UTF-8"
    config.accessManager { case (handler, ctx, roles) =>
      Option.when(ctx.basicAuthCredentialsExist()) {
        val cred = ctx.basicAuthCredentials()
        interactions.authenticate(cred.getUsername, cred.getPassword)
      }.flatten match {
        case None       =>
          ctx.status(401).header(Header.WWW_AUTHENTICATE, "Basic")
        case Some(user) =>
          ctx.attribute("user", user)
          handler.handle(ctx)
      }
    }
    config.addStaticFiles(staticPath.pathAsString, Location.EXTERNAL)
    config.compressionStrategy(new Brotli(): @nowarn, new Gzip())
    config.showJavalinBanner = false
  }


  val landingString = pages.fullrender(pages.landingTag)
  val toolsString   = pages.fullrender(pages.toolsPage)


  def wsSetup() = {

    val wspath     = "ws"
    val properties = Properties(heartbeatDelay = 3.seconds, heartbeatTimeout = 10.seconds)

    val listener = new Listener[JavalinWS] {
      self =>
      protected def startListening(connectionEstablished: Connected[JavalinWS]): Try[Listening] = {
        jl.ws(wspath, new Consumer[WsHandler] {
          override def accept(ws: WsHandler): Unit =
            LociJavalinWSHandler.handleConnection(ws, wspath, properties, self, connectionEstablished.fire)
        })
        Success(new Listening {
          def stopListening(): Unit = ()
        })
      }
    }

    val registry = new Registry
    interactions.bindGlobalData(registry)

    val userSocketCache: mutable.Map[User.Id, Signal[BookmarksMap]] = mutable.Map.empty

    import rescala.default.implicitScheduler

    LociDist.distributePerRemote({rr =>
      val user = rr.protocol.asInstanceOf[JavalinWS].javalinContext.attribute[User]("user")
      userSocketCache.getOrElseUpdate(user.id, interactions.handleBookmarks(user.id))
    }, registry)(Bindings.bookmarksMapBindig)
    //LociDist.distribute(handleBookmarks(userid), registry)(Bindings.bookmarksMapBindig)

    registry.listen(listener)
  }

  def setup(): Javalin = {
    jl.get("version", ctx => {
      ctx.contentType("text/plain")
      ctx.result(Viscel.version)
    })
    jl.get("", { ctx =>
      ctx.result(landingString)
    })
    jl.get("blob/:hash", { ctx =>
      val sha1      = ctx.pathParamMap.get("hash")
      val filename  = File(blobStore.hashToPath(sha1))
      val mediatype = Option(ctx.queryParamMap.get("mime")).flatMap(_.asScala.headOption).getOrElse("image")
      ctx.contentType(mediatype)
      ctx.header(Header.CACHE_CONTROL, "max-age=31557600, public, immutable")
      ctx.result(filename.newInputStream)
    })
    jl.get("stop", { ctx =>
      if (ctx.attribute[User]("user").admin) {
        terminate()
      }
      ctx.result("")
    })
    jl.get("tools", { ctx =>
      ctx.result(toolsString)
    })
    jl.get("import", { ctx =>
      if (ctx.attribute[User]("user").admin) {
        val params = ctx.queryParamMap().asScala
        List("id", "name", "path").flatMap(key => params.get(key).flatMap(_.asScala.headOption)) match {
          case List(id, name, path) =>
            import scala.concurrent.ExecutionContext.Implicits.global
            ctx.result(Future(folderImporter.importFolder(path, Vid.from(s"Import_$id"), name))
                         .map(_ => "success").asJava.toCompletableFuture)
        }
      }
      else {
        ctx.status(403)
        ctx.result("")
      }
    })
    jl.get("add", { ctx =>
      if (ctx.attribute[User]("user").admin) {
        val params = ctx.queryParamMap().asScala
        List("url").flatMap(key => params.get(key).flatMap(_.asScala.headOption)) match {
          case List(url) =>
            import scala.concurrent.ExecutionContext.Implicits.global
            ctx.result(interactions.addNarratorsFrom(url)
                        .map(v => s"found $v")
                        .asJava.toCompletableFuture)
        }
      }
      else {
        ctx.status(403)
        ctx.result("")
      }
    })
  }
}
