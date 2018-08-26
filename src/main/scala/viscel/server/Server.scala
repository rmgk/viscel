package viscel.server

import java.nio.file.Path

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, HttpChallenges}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.AuthenticationResult
import akka.http.scaladsl.server.directives.BasicDirectives.extractExecutionContext
import akka.http.scaladsl.server.{Directive, Route}
import loci.communicator.ws.akka._
import loci.registry.Registry
import org.scalactic.TypeCheckedTripleEquals._
import rescala.default.{Evt, implicitScheduler}
import viscel.ReplUtil
import viscel.crawl.WebRequestInterface
import viscel.narration.Narrator
import viscel.shared.{Bindings, Vid}
import viscel.shared.Log.{Server => Log}
import viscel.store.{BlobStore, NarratorCache, User, Users}

import scala.collection.immutable.Map
import scala.collection.mutable
import scala.concurrent.Future
import scala.util.{Failure, Success}

class Server(userStore: Users,
             contentLoader: ContentLoader,
             blobStore: BlobStore,
             requestUtil: WebRequestInterface,
             terminate: () => Unit,
             narratorHint: Evt[(Narrator, Boolean)],
             pages: ServerPages,
             replUtil: ReplUtil,
             system: ActorSystem,
             narratorCache: NarratorCache,
             postsPath: Path,
            ) {

  val userSocketCache: mutable.Map[String, Route] = mutable.Map.empty
  def userSocket(user: String): Route = synchronized {
    userSocketCache.getOrElseUpdate(user, {
      Log.debug(s"create new websocket for $user")
      val webSocket = WebSocketListener()
      val registry = new Registry
      registry.listen(webSocket)

      registry.bind(Bindings.contents)(contentLoader.narration)
      registry.bind(Bindings.descriptions)(() => contentLoader.narrations())
      registry.bind(Bindings.hint) { (description, force) =>
        val nar = narratorCache.get(description.id)
        if (nar.isDefined) narratorHint.fire(nar.get -> force)
        else Log.warn(s"got hint for unknown $description")
      }
      registry.bind(Bindings.bookmarks) { set =>
        userStore.get(user).map { user =>
          set.fold(user) { case (desc, pos) =>
            setBookmark(user, pos, desc.id)
          }.bookmarks
        }.getOrElse(Map.empty)
      }
      webSocket
    })
  }


  private def setBookmark(user: User, bmpos: Int, colid: Vid): User = {
    if (bmpos > 0) userStore.userUpdate(user.setBookmark(colid, bmpos))
    else userStore.userUpdate(user.removeBookmark(colid))
  }


  def authenticate(credentials: Option[BasicHttpCredentials]): Option[User] = credentials match {
    case Some(BasicHttpCredentials(user, password)) =>
      Log.trace(s"login: $user $password")
      // time("login") {
      if (user.matches("\\w+")) {
        userStore.getOrAddFirstUser(user, User(user, password, admin = true, Map())).filter(_.password === password)
      }
      else None
    // }
    case None => None
  }


  def sprayLikeBasicAuth[T](realm: String, authenticator: Option[BasicHttpCredentials] => Option[T]): Directive[Tuple1[T]] =
    extractExecutionContext.flatMap { implicit ec ⇒
      authenticateOrRejectWithChallenge[BasicHttpCredentials, T] { cred ⇒
        authenticator(cred) match {
          case Some(t) ⇒ Future.successful(AuthenticationResult.success(t))
          case None ⇒ Future.successful(AuthenticationResult.failWithChallenge(HttpChallenges.basic(realm)))
        }
      }
    }

  def route: Route = {
    decodeRequest {
      encodeResponse {
        sprayLikeBasicAuth("Username is used to store configuration; Passwords are saved in plain text; User is created on first login",
          authenticate) { user =>
          extractRequest { request =>
            request.headers.find(h => h.is("x-path-prefix")) match {
              case None => defaultRoute(user)
              case Some(prefix) => pathPrefix(prefix.value()) {defaultRoute(user)}
            }
          }
        }
      }
    }
  }

  // we use the enclosing ActorContext's or ActorSystem's dispatcher for our Futures and Scheduler
  import system.dispatcher


  def defaultRoute(user: User): Route =
    path("") {
      complete(pages.landing)
    } ~
      path("stop") {
        if (!user.admin) reject
        else complete {
          Future {
            Thread.sleep(100)
            terminate()
            Log.info("shutdown complete")
          }
          "shutdown"
        }
      } ~
      path("css") {
        getFromFile("web/target/web/sass/main/stylesheets/style.css") ~
          getFromResource("style.css")
      } ~
      path("style.css.map") {
        getFromFile("web/target/web/sass/main/stylesheets/style.css.map") ~
          getFromResource("style.css.map")
      } ~
      path("js") {
        getFromFile("web/target/scala-2.12/web-opt.js") ~ getFromFile("web/target/scala-2.12/web-fastopt.js") ~
          getFromResource("web-opt.js") ~ getFromResource("web-fastopt.js")
      } ~
      path("ws") {
        userSocket(user.id)
      } ~
      path("web-fastopt.js.map") {
        getFromFile("web/target/scala-2.12/web-fastopt.js.map")
      } ~
      path("web-opt.js.map") {
        getFromFile("web/target/scala-2.12/web-opt.js.map")
      } ~
      pathPrefix("blob" / Segment) { (sha1) =>
        val filename = blobStore.hashToPath(sha1).toFile
        pathEnd {getFromFile(filename)} ~
          path(Segment / Segment) { (part1, part2) =>
            getFromFile(filename, ContentType(MediaTypes.getForKey(part1 -> part2).getOrElse(MediaTypes.`image/jpeg`), () => HttpCharsets.`UTF-8`))
          }
      } ~
      path("export" / Segment) { (id) =>
        if (!user.admin) reject
        else onComplete(Future(replUtil.export(Vid.from(id)))) {
          case Success(v) => complete("success")
          case Failure(e) => complete(e.toString())
        }
      } ~
      path("import") {
        if (!user.admin) reject
        else parameters(('id.as[String], 'name.as[String], 'path.as[String])) { (id, name, path) =>
          onComplete(Future(replUtil.importFolder(path, Vid.from(s"Import_$id"), name))) {
            case Success(v) => complete("success")
            case Failure(e) => complete(e.toString())
          }
        }
      } ~
      path("add") {
        if (!user.admin) reject
        else parameter('url.as[String]) { url =>
          onComplete(narratorCache.add(url, requestUtil)) {
            case Success(v) => complete(s"found $v")
            case Failure(e) => complete {e.getMessage}
          }
        }
      } ~
      path("reload") {
        if (!user.admin) reject
        else complete {
          narratorCache.updateCache()
          "done"
        }
      } ~
      path("tools") {
        complete(pages.toolsResponse)
      }


  def rejectNone[T](opt: => Option[T])(route: T => Route): Route = opt.map {route}.getOrElse(reject)
}
