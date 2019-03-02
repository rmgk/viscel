package viscel.server

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, HttpChallenges}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.AuthenticationResult
import akka.http.scaladsl.server.directives.BasicDirectives.extractExecutionContext
import akka.http.scaladsl.server.{Directive, Route}
import org.scalactic.TypeCheckedTripleEquals._
import viscel.ReplUtil
import viscel.shared.Log.{Server => Log}
import viscel.shared.Vid
import viscel.store.{BlobStore, User, Users}

import scala.collection.immutable.Map
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class Server(userStore: Users,
             blobStore: BlobStore,
             terminate: () => Unit,
             pages: ServerPages,
             replUtil: ReplUtil,
             interactions: Interactions,
             executionContext: ExecutionContext
            ) {

  implicit def implicitExecutionContext: ExecutionContext = executionContext

  def authenticate(credentials: Option[BasicHttpCredentials]): Option[User] = credentials match {
    case Some(BasicHttpCredentials(user, password)) =>
      Log.trace(s"login: $user $password")
      if (user.matches("\\w+")) {
        userStore.getOrAddFirstUser(user, User(user, password, admin = true, Map())).filter(_.password === password)
      }
      else None
    case None => None
  }


  def sprayLikeBasicAuth[T](realm: String, authenticator: Option[BasicHttpCredentials] => Option[T]): Directive[Tuple1[T]] =
    extractExecutionContext.flatMap { _ =>
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
        sprayLikeBasicAuth(
          realm = "Username is used to store configuration; Passwords are saved in plain text; User is created on first login",
          authenticator = authenticate) { user =>
          extractRequest { request =>
            request.headers.find(h => h.is("x-path-prefix")) match {
              case None         => defaultRoute(user)
              case Some(prefix) => pathPrefix(prefix.value()) {defaultRoute(user)}
            }
          }
        }
      }
    }
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
        interactions.userSocket(user)
      } ~
      path("web-fastopt.js.map") {
        getFromFile("web/target/scala-2.12/web-fastopt.js.map")
      } ~
      path("web-opt.js.map") {
        getFromFile("web/target/scala-2.12/web-opt.js.map")
      } ~
      pathPrefix("blob" / Segment) { sha1 =>
        val filename = blobStore.hashToPath(sha1).toFile
        pathEnd {getFromFile(filename)} ~
          path(Segment / Segment) { (part1, part2) =>
            getFromFile(filename, ContentType(MediaTypes.getForKey(part1 -> part2).getOrElse(MediaTypes.`image/jpeg`), () => HttpCharsets.`UTF-8`))
          }
      } ~
      path("export" / Segment) { id =>
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
          onComplete(interactions.addNarratorsFrom(url)) {
            case Success(v) => complete(s"found $v")
            case Failure(e) => complete(e.toString)
          }
        }
      } ~
      path("tools") {
        complete(pages.toolsResponse)
      }
}
