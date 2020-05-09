package viscel.server

import java.nio.file.Path

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, HttpChallenges}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.AuthenticationDirective
import viscel.FolderImporter
import viscel.shared.Vid
import viscel.store.{BlobStore, User, Users}

import scala.concurrent.Future
import scala.util.{Failure, Success}

class Server(userStore: Users,
             blobStore: BlobStore,
             terminate: () => Unit,
             pages: ServerPages,
             folderImporter: FolderImporter,
             interactions: Interactions,
             staticPath: Path
            ) {


  def route: Route = decodeRequest(subPathRoute(staticRoute ~ dynamicRoute))

  def subPathRoute(continue: Route): Route =
    extractRequest { request =>
      request.headers.find(h => h.is("x-path-prefix")) match {
        case None         => continue
        case Some(prefix) => pathPrefix(prefix.value()) {continue}
      }
    }

  def stc(name: String, file: String): Route =
    path(name){getFromFile(staticPath.resolve(file).toFile)}

  val staticRoute: Route =
    List("app-fastopt.js.map", "style.css.map", "serviceworker.js", "manifest.json", "icon.png",
         "localforage.min.js")
      .map(str => stc(str, str + ".gz"))
      .foldLeft(
        path("version") {complete(viscel.Viscel.version)}~
        stc("js", "app-fastopt.js.gz") ~
        stc("css", "style.css.gz")
        )(_ ~ _)


  val basicAuth: AuthenticationDirective[User] = {
    val realm = "Username is used to store configuration; Passwords are saved in plain text; User is created on first login"

    authenticateOrRejectWithChallenge[BasicHttpCredentials, User] { credentials =>
      val userOption = credentials.flatMap { bc =>
        interactions.authenticate(bc.username, bc.password)
      }
      Future.successful(userOption.toRight(HttpChallenges.basic(realm)))
    }
  }

  val dynamicRoute: Route = basicAuth(authedRoute)


  val blobRoute: Route =
    pathPrefix("blob" / Segment) { sha1 =>
      val filename = blobStore.hashToPath(sha1).toFile
      path(Segment / Segment) { (part1, part2) =>
        getFromFile(filename,
                    ContentType(MediaTypes.getForKey(part1 -> part2).getOrElse(MediaTypes.`image/jpeg`),
                                () => HttpCharsets.`UTF-8`))
      } ~ getFromFile(filename)
    }

  def authedRoute(user: User): Route = blobRoute ~ encodeResponse {
    path("") {
      complete(pages.landing)
    } ~
    path("stop") {
      if (!user.admin) reject
      else complete {
        terminate()
        "shutdown"
      }
    } ~
    path("ws") {
      interactions.userSocket(user.id)
    } ~
    path("import") {
      if (!user.admin) reject
      else parameters(("id".as[String], "name".as[String], "path".as[String])) { (id, name, path) =>
        extractExecutionContext { ec =>
          onComplete(Future(folderImporter.importFolder(path, Vid.from(s"Import_$id"), name))(ec)) {
            case Success(v) => complete("success")
            case Failure(e) => complete(e.toString())
          }
        }
      }
    } ~
    path("add") {
      if (!user.admin) reject
      else parameter("url".as[String]) { url =>
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
}
