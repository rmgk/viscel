package viscel.server

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, HttpChallenges}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.AuthenticationResult
import akka.http.scaladsl.server.directives.BasicDirectives.extractExecutionContext
import akka.http.scaladsl.server.{Directive, Route}
import io.circe.generic.auto._
import org.scalactic.TypeCheckedTripleEquals._
import rescala.Evt
import viscel.ReplUtil
import viscel.crawl.RequestUtil
import viscel.narration.Narrator
import viscel.scribe.Scribe
import viscel.shared.Log.{Server => Log}
import viscel.store.{BlobStore, NarratorCache, User, Users}

import scala.collection.immutable.Map
import scala.concurrent.Future
import scala.util.{Failure, Success}

class Server(userStore: Users,
             scribe: Scribe,
             blobStore: BlobStore,
             requestUtil: RequestUtil,
             terminate: () => Unit,
             narratorHint: Evt[(Narrator, Boolean)],
             pages: ServerPages,
             replUtil: ReplUtil,
             system: ActorSystem,
             narratorCache: NarratorCache,
            ) {

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


  def handleBookmarksForm(user: User)(continue: User => Route): Route =
    formFields(('narration.as[String].?, 'bookmark.as[Int].?)) { (colidOption, bmposOption) =>
      val newUser = for (bmpos <- bmposOption; colid <- colidOption) yield {
        if (bmpos > 0) userStore.userUpdate(user.setBookmark(colid, bmpos))
        else userStore.userUpdate(user.removeBookmark(colid))
      }
      continue(newUser.getOrElse(user))
    }

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
        getFromResource("style.css")
      } ~
      path("js") {
        getFromFile("js/target/scala-2.12/viscel-js-opt.js") ~ getFromFile("js/target/scala-2.12/viscel-js-fastopt.js") ~
          getFromResource("viscel-js-opt.js") ~ getFromResource("viscel-js-fastopt.js")
      } ~
      path("viscel-js-fastopt.js.map") {
        getFromFile("js/target/scala-2.12/viscel-js-fastopt.js.map")
      } ~
      path("viscel-js-opt.js.map") {
        getFromFile("js/target/scala-2.12/viscel-js-opt.js.map")
      } ~
      path("bookmarks") {
        handleBookmarksForm(user)(newUser => complete(pages.bookmarks(newUser)))
      } ~
      path("narrations") {
        complete(pages.jsonResponse(pages.narrations()))
      } ~
      path("narration" / Segment) { collectionId =>
        rejectNone(pages.narration(collectionId)) { content =>
          complete(pages.jsonResponse(content))
        }
      } ~
      pathPrefix("blob" / Segment) { (sha1) =>
        val filename = blobStore.hashToPath(sha1).toFile
        pathEnd {getFromFile(filename)} ~
          path(Segment / Segment) { (part1, part2) =>
            getFromFile(filename, ContentType(MediaTypes.getForKey(part1 -> part2).getOrElse(MediaTypes.`image/jpeg`), () => HttpCharsets.`UTF-8`))
          }
      } ~
      pathPrefix("hint") {
        path("narrator" / Segment) { narratorID =>
          rejectNone(narratorCache.get(narratorID)) { nar =>
            parameters('force.as[Boolean].?) { force =>
              complete {
                narratorHint.fire(nar -> force.getOrElse(false))
                force.toString
              }
            }
          }
        }
      } ~
      path("export" / Segment) { (id) =>
        if (!user.admin) reject
        else onComplete(Future(replUtil.export(id))) {
          case Success(v) => complete("success")
          case Failure(e) => complete(e.toString())
        }
      } ~
      path("import") {
        if (!user.admin) reject
        else parameters(('id.as[String], 'name.as[String], 'path.as[String])) { (id, name, path) =>
          onComplete(Future(replUtil.importFolder(path, s"Import_$id", name))) {
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
