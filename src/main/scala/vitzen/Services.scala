package vitzen

import java.nio.file.{Files, Path}
import java.util.concurrent.{LinkedBlockingQueue, ThreadPoolExecutor, TimeUnit}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.{RouteResult, RoutingLog}
import akka.http.scaladsl.settings.{ParserSettings, RoutingSettings}
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.ActorMaterializer
import org.asciidoctor.Asciidoctor
import vitzen.data.AsciiData
import vitzen.logging.{Level, Logger}
import vitzen.server.{Server, ServerPages}
import vitzen.store.Users

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

class Services(log: Logger, relativeBasedir: Path, relativeDatadir: Path, interface: String, port: Int) {


  /* ====== paths ====== */

  def create(p: Path): Path = {
    Files.createDirectories(p)
    p
  }
  val basepath: Path = relativeBasedir.toAbsolutePath
  val datadir: Path = basepath.resolve(relativeDatadir)
  val usersdir: Path = basepath.resolve("users")
  val contentdir: Path = basepath.resolve("content")


  /* ====== execution ====== */

  lazy val system = ActorSystem()
  lazy val materializer: ActorMaterializer = ActorMaterializer()(system)
  lazy val http: HttpExt = Http(system)
  lazy val executionContext: ExecutionContextExecutor = ExecutionContext.fromExecutor(new ThreadPoolExecutor(
    0, 1, 1L, TimeUnit.SECONDS, new LinkedBlockingQueue[Runnable]))

  /* ====== data ====== */
  lazy val userStore = new Users(log.copy("User: "),usersdir)
  lazy val asciidoctor: Asciidoctor = Asciidoctor.Factory.create()
  lazy val asciiData: AsciiData = new AsciiData(log.copy("Data: "), asciidoctor, datadir)

  /* ====== main webserver ====== */

  lazy val serverPages = new ServerPages(asciiData, contentdir)
  lazy val server = new Server(
    log.copy("Serv: ", Level.Trace),
    userStore, () => terminateServer(),
    serverPages,
    system,
    contentdir)
  lazy val serverBinding: Future[ServerBinding] = http.bindAndHandle(
    RouteResult.route2HandlerFlow(server.route)(
      RoutingSettings.default(system),
      ParserSettings.default(system),
      materializer,
      RoutingLog.fromActorSystem(system)),
    interface, port)(materializer)

  def startServer(): Future[ServerBinding] = serverBinding
  def terminateServer(): Unit = {
    serverBinding
      .flatMap(_.unbind())(system.dispatcher)
      .onComplete { _ =>
        system.terminate()
      }(system.dispatcher)
  }



}
