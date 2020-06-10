package viscel.server


import java.io.ByteArrayInputStream

import better.files._
import io.javalin.Javalin
import io.javalin.core.compression.Gzip
import io.javalin.core.util.Header
import io.javalin.http.staticfiles.Location
import loci.communicator.ws.javalin.WS
import loci.communicator.ws.javalin.WS.Properties
import loci.registry.Registry
import rescala.default.Signal
import rescala.extra.distributables.LociDist
import viscel.shared.BookmarksMap.BookmarksMap
import viscel.shared.{Bindings, Vid}
import viscel.store.{BlobStore, RowStoreV4, User}
import viscel.{FolderImporter, Viscel}

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._


class JavalinServer(blobStore: BlobStore,
                    terminate: () => Unit,
                    pages: ServerPages,
                    folderImporter: FolderImporter,
                    interactions: Interactions,
                    staticPath: File,
                    urlPrefix: String,
                    rowStore: RowStoreV4
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
      }.flatten.orElse{
        val cm = ctx.cookieMap().asScala
        (cm.get("viscel-user"), cm.get("viscel-password")) match {
          case (Some(user), Some(password)) => interactions.authenticate(user, password)
          case _ => None
        }
      } match {
        case None       =>
          ctx.status(401).header(Header.WWW_AUTHENTICATE, "Basic")
        case Some(user) =>
          ctx.attribute("user", user)
          ctx.cookie("viscel-user",user.id, 5_000_000)
          ctx.cookie("viscel-password", user.password, 5_000_000)
          handler.handle(ctx)
      }
    }
    // seems buggy
    //config.precompressStaticFiles = true
    config.addStaticFiles(staticPath.pathAsString, Location.EXTERNAL)
    config.compressionStrategy(null, new Gzip())
    config.showJavalinBanner = false
  }


  val landingString = pages.fullrender(pages.landingTag)
  val toolsString   = pages.fullrender(pages.toolsPage)


  def wsSetup() = {

    val wspath     = "ws"
    val properties = Properties(heartbeatDelay = 3.seconds, heartbeatTimeout = 10.seconds)

    val registry = new Registry
    interactions.bindGlobalData(registry)

    val userSocketCache: mutable.Map[User.Id, Signal[BookmarksMap]] = mutable.Map.empty

    import rescala.default.implicitScheduler

    LociDist.distributePerRemote({rr =>
      val user = rr.protocol.asInstanceOf[loci.communicator.ws.javalin.WS].context.attribute[User]("user")
      userSocketCache.getOrElseUpdate(user.id, interactions.handleBookmarks(user.id))
    }, registry)(Bindings.bookmarksMapBindig)
    //LociDist.distribute(handleBookmarks(userid), registry)(Bindings.bookmarksMapBindig)

    registry.listen(WS(jl, wspath, properties))
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
      if (filename.exists) {
        ctx.header(Header.CACHE_CONTROL, "max-age=31557600, public, immutable")
        ctx.result(filename.newInputStream)
      } else {
        ctx.status(404)
        ctx.result("")
      }
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
    jl.get("db4/:vid", {ctx =>
      val vid = Vid.from(ctx.pathParam("vid"))
      val bytes = rowStore.file(vid).byteArray
      ctx.status(200)
      ctx.result(new ByteArrayInputStream(bytes))
      ctx.contentType("text/plain; charset=UTF-8")
    })
  }
}
