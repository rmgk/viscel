package vitzen.server

import java.nio.file.Path

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, HttpChallenges}
import akka.http.scaladsl.server.Directives.{path, _}
import akka.http.scaladsl.server.directives.AuthenticationResult
import akka.http.scaladsl.server.directives.BasicDirectives.extractExecutionContext
import akka.http.scaladsl.server.{Directive, Route}
import org.scalactic.TypeCheckedTripleEquals._
import vitzen.logging.Logger
import vitzen.store.{User, Users}

import scala.concurrent.Future

class Server(log: Logger,
             userStore: Users,
             terminate: () => Unit,
             pages: ServerPages,
             system: ActorSystem,
             contentPath: Path,
            ) {

  def authenticate(credentials: Option[BasicHttpCredentials]): Option[User] = credentials match {
    case Some(BasicHttpCredentials(user, password)) =>
      log.trace(s"login: $user $password")
      if (user.matches("\\w+")) {
        userStore.getOrAddFirstUser(user, User(user, password, admin = true)).filter(_.password === password)
      }
      else None
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
            log.trace(s"request: ${request.uri}")
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
      complete(pages.archive())
    } ~
      path("stop") {
        if (!user.admin) reject
        else complete {
          Future {
            Thread.sleep(100)
            terminate()
            log.info("shutdown complete")
          }
          "shutdown"
        }
      } ~
      path("css") {
          getFromResource("main.css")
      } ~
      path("content" / Segments) { name =>
        val path = name.filter(_ != "..").mkString("/")
        if (path.endsWith("adoc"))
          complete(pages.getContent(path))
        else
          getFromFile(contentPath.resolve(path).toFile)
      } ~
      //      pathPrefix("content" / Segment) { (sha1) =>
      //        pathEnd {getFromFile(filename)} ~
      //          path(Segment / Segment) { (part1, part2) =>
      //            getFromFile(filename, ContentType(MediaTypes.getForKey(part1 -> part2).getOrElse(MediaTypes.`image/jpeg`), () => HttpCharsets.`UTF-8`))
      //          }
      //      } ~
//      pathPrefix("hint") {
//        path("narrator" / Segment) { narratorID =>
//          rejectNone(narratorCache.get(narratorID)) { nar =>
//            parameters('force.as[Boolean].?) { force =>
//              complete {
//                narratorHint.fire(nar -> force.getOrElse(false))
//                force.toString
//              }
//            }
//          }
//        }
//      } ~
      path("tools") {
        complete(pages.toolsResponse)
      }

  def rejectNone[T](opt: => Option[T])(route: T => Route): Route = opt.map {route}.getOrElse(reject)
}
