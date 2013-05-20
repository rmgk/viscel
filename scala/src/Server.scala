import akka.actor.{ActorSystem, Props, Actor}
import akka.io.IO
import spray.can.Http

import scala.concurrent.duration._
import akka.pattern.ask
import akka.util.Timeout
import akka.actor._
import spray.can.Http
import spray.can.server.Stats
import spray.util._
import spray.http._
import HttpMethods._
import MediaTypes._

import java.io.File
import org.parboiled.common.FileUtils
import scala.concurrent.duration._
import spray.routing.{HttpService, RequestContext}
import spray.routing.directives.CachingDirectives
import spray.httpx.marshalling.Marshaller
import spray.httpx.encoding.Gzip
import CachingDirectives._

import scala.slick.session.Database
//import Database.threadLocalSession
import scala.slick.jdbc.{GetResult, StaticQuery => Q}
import Q.interpolation

import scalax.io.Resource
import scalax.file.Path

import java.net.URLEncoder


object CollectionDB {

	lazy val db = Database.forURL("jdbc:sqlite:collections.db", driver = "org.sqlite.JDBC")
	implicit lazy val session = db.createSession()

	def go() {
		var q = sql"SELECT sha1, state FROM Universal_Twokinds".as[(String, String)]
		q.list foreach println
		// sqlu"INSERT INTO Universal_Twokinds (position, sha1) VALUES (10000,'hallo welt')".execute
	}

	def getFiles: IndexedSeq[String] = {
		(Path.fromString("D:/wpc/export") * {(p: Path) => p.name.matches(""".*(jpe?g|gif|png|bmp)$""")}).toIndexedSeq map {p => p.name.toString } sorted
	}

	def filelist = HttpResponse(
		entity = HttpEntity(`text/html`,
			<html>
				<body>
					<h1>Filelist</h1>
					<ul>
					{ CollectionDB.getFiles map {s => <li><a href={"/file/" + s}>{s}</a></li>} }
					</ul>
				</body>
			</html>.toString()
		)
	)
}



object Server extends App {

	implicit val system = ActorSystem()

	// the handler actor replies to incoming HttpRequests
	val handler = system.actorOf(Props[DemoService], name = "handler")

	// create and start our service actor
	val service = system.actorOf(Props[DemoServiceActor], "demo-service")

	IO(Http) ! Http.Bind(handler, interface = "0", port = 8080)

	// start a new HTTP server on port 8080 with our service actor as the handler
	IO(Http) ! Http.Bind(service, "0", port = 8081)
}

class DemoService extends Actor with SprayActorLogging {

	implicit val timeout: Timeout = 1.second // for the actor 'asks'
	import context.dispatcher // ExecutionContext for the futures and scheduler

	val getfile = """.*/([^/]+)""".r

	def receive = {
			// when a new connection comes in we register ourselves as the connection handler
			case _: Http.Connected => sender ! Http.Register(self)

			case HttpRequest(GET, Uri.Path("/"), _, _, _) =>
				sender ! index

			case HttpRequest(GET, Uri.Path("/ping"), _, _, _) =>
				sender ! HttpResponse(entity = "PONG!")

			case HttpRequest(GET, Uri.Path("/files"), _, _, _) =>
				log info "got files request"
				sender ! CollectionDB.filelist

			case HttpRequest(GET, uri, _, _, _) if uri.path.toString startsWith "/file/" =>
				log.info("got file request {}: {}", uri ,uri.path.tail.tail.tail.head)
				val time = System.currentTimeMillis()
				sender ! image(uri.path.tail.tail.tail.head.toString)
				log.info("took {} milliseconds", System.currentTimeMillis() - time)

			case HttpRequest(GET, Uri.Path("/stream"), _, _, _) =>
				val peer = sender // since the Props creator is executed asyncly we need to save the sender ref
				context actorOf Props(new Streamer(peer, 25))

			case HttpRequest(GET, Uri.Path("/server-stats"), _, _, _) =>
				val client = sender
				context.actorFor("/user/IO-HTTP/listener-0") ? Http.GetStats onSuccess {
					case x: Stats => client ! statsPresentation(x)
				}

			case HttpRequest(GET, Uri.Path("/crash"), _, _, _) =>
				sender ! HttpResponse(entity = "About to throw an exception in the request handling actor, " +
					"which triggers an actor restart")
				sys.error("BOOM!")

			case HttpRequest(GET, Uri.Path(path), _, _, _) if path startsWith "/timeout" =>
				log.info("Dropping request, triggering a timeout")

			case HttpRequest(GET, Uri.Path("/stop"), _, _, _) =>
				sender ! HttpResponse(entity = "Shutting down in 1 second ...")
				context.system.scheduler.scheduleOnce(1.second) { context.system.shutdown() }

			case _: HttpRequest => sender ! HttpResponse(status = 404, entity = "Unknown resource!")

			case Timedout(HttpRequest(_, Uri.Path("/timeout/timeout"), _, _, _)) =>
				log.info("Dropping Timeout message")

			case Timedout(HttpRequest(method, uri, _, _, _)) =>
				sender ! HttpResponse(
					status = 500,
					entity = "The " + method + " request to '" + uri + "' has timed out..."
				)
		}

		////////////// helpers //////////////

		def image(name: String) = {
			val path = Path.fromString("D:/wpc/export/" + name)
			if (path.isFile) {
				val mime = MediaTypes.forExtension(path.extension.getOrElse("")).getOrElse(MediaTypes.`image/jpeg`)
				HttpResponse(entity = HttpEntity(mime, path.byteArray))
			}
			else {
				HttpResponse(entity = HttpEntity(MediaTypes.`text/plain`, "file not found"))
			}
		}

		lazy val index = HttpResponse(
			entity = HttpEntity(`text/html`,
				<html>
					<body>
						<h1>Say hello to <i>spray-can</i>!</h1>
						<p>Defined resources:</p>
						<ul>
							<li><a href="/ping">/ping</a></li>
							<li><a href="/files">/files</a></li>
							<li><a href="/stream">/stream</a></li>
							<li><a href="/server-stats">/server-stats</a></li>
							<li><a href="/crash">/crash</a></li>
							<li><a href="/timeout">/timeout</a></li>
							<li><a href="/timeout/timeout">/timeout/timeout</a></li>
							<li><a href="/stop">/stop</a></li>
						</ul>
					</body>
				</html>.toString()
			)
		)

		def statsPresentation(s: Stats) = HttpResponse(
			entity = HttpEntity(`text/html`,
				<html>
					<body>
						<h1>HttpServer Stats</h1>
						<table>
							<tr><td>uptime:</td><td>{s.uptime.formatHMS}</td></tr>
							<tr><td>totalRequests:</td><td>{s.totalRequests}</td></tr>
							<tr><td>openRequests:</td><td>{s.openRequests}</td></tr>
							<tr><td>maxOpenRequests:</td><td>{s.maxOpenRequests}</td></tr>
							<tr><td>totalConnections:</td><td>{s.totalConnections}</td></tr>
							<tr><td>openConnections:</td><td>{s.openConnections}</td></tr>
							<tr><td>maxOpenConnections:</td><td>{s.maxOpenConnections}</td></tr>
							<tr><td>requestTimeouts:</td><td>{s.requestTimeouts}</td></tr>
						</table>
					</body>
				</html>.toString()
			)
		)

		class Streamer(client: ActorRef, count: Int) extends Actor with SprayActorLogging {
			log.debug("Starting streaming response ...")

			// we use the successful sending of a chunk as trigger for scheduling the next chunk
			client ! ChunkedResponseStart(HttpResponse(entity = " " * 2048)).withAck(Ok(count))

			def receive = {
				case Ok(0) =>
					log.info("Finalizing response stream ...")
					client ! MessageChunk("\nStopped...")
					client ! ChunkedMessageEnd()
					context.stop(self)

				case Ok(remaining) =>
					log.info("Sending response chunk ...")
					context.system.scheduler.scheduleOnce(100 millis span) {
						client ! MessageChunk(DateTime.now.toIsoDateTimeString + ", ").withAck(Ok(remaining - 1))
					}

				case x: Http.ConnectionClosed =>
					log.info("Canceling response stream due to {} ...", x)
					context.stop(self)
			}

			// simple case class whose instances we use as send confirmation message for streaming chunks
			case class Ok(remaining: Int)
		}
}



// we don't implement our route structure directly in the service actor because
// we want to be able to test it independently, without having to spin up an actor
class DemoServiceActor extends Actor with DemoServiceRoute {

	// the HttpService trait defines only one abstract member, which
	// connects the services environment to the enclosing actor or test
	def actorRefFactory = context

	// this actor only runs our route, but you could add
	// other things here, like request stream processing,
	// timeout handling or alternative handler registration
	def receive = runRoute(demoRoute)
}


// this trait defines our service behavior independently from the service actor
trait DemoServiceRoute extends HttpService {

	// we use the enclosing ActorContext's or ActorSystem's dispatcher for our Futures and Scheduler
	implicit def executionContext = actorRefFactory.dispatcher

	val demoRoute = {
		get {
			path("") {
				respondWithMediaType(`text/html`) { // XML is marshalled to `text/xml` by default, so we simply override here
					complete(index)
				}
			} ~
			path("ping") {
				complete("PONG!")
			} ~
			path("files") {
				complete(CollectionDB.filelist)
			} ~
			path("file" / PathElement) { p =>
				getFromFile(new File("D:/wpc/export/" + p))
			} ~
			path("stream1") {
				respondWithMediaType(`text/html`) {
					// we detach in order to move the blocking code inside the simpleStringStream off the service actor
					detachTo(singleRequestServiceActor) {
						complete(simpleStringStream)
					}
				}
			} ~
			path("stream2") {
				sendStreamingResponse
			} ~
			path("stream-large-file") {
				encodeResponse(Gzip) {
					getFromFile(largeTempFile)
				}
			} ~
			path("stats") {
				complete {
					actorRefFactory.actorFor("/user/IO-HTTP/listener-1")
						.ask(Http.GetStats)(1.second)
						.mapTo[Stats]
				}
			} ~
			path("timeout") { ctx =>
				// we simply let the request drop to provoke a timeout
			} ~
			path("cached") {
				cache(simpleRouteCache) { ctx =>
					in(1500.millis) {
						ctx.complete("This resource is only slow the first time!\n" +
							"It was produced on " + DateTime.now.toIsoDateTimeString + "\n\n" +
							"(Note that your browser will likely enforce a cache invalidation with a\n" +
							"`Cache-Control: max-age=0` header when you click 'reload', so you might need to `curl` this\n" +
							"resource in order to be able to see the cache effect!)")
					}
				}
			} ~
			path("crash") { ctx =>
				sys.error("crash boom bang")
			} ~
			path("fail") {
				failWith(new RuntimeException("aaaahhh"))
			}
		} ~
		(post | parameter('method ! "post")) {
			path("stop") {
				complete {
					in(1.second){ actorSystem.shutdown() }
					"Shutting down in 1 second..."
				}
			}
		}
	}

	lazy val simpleRouteCache = routeCache()

	lazy val index =
		<html>
			<body>
				<h1>Say hello to <i>spray-routing</i> on <i>spray-can</i>!</h1>
				<p>Defined resources:</p>
				<ul>
					<li><a href="/ping">/ping</a></li>
					<li><a href="/files">/files</a></li>
					<li><a href="/stream1">/stream1</a> (via a Stream[T])</li>
					<li><a href="/stream2">/stream2</a> (manually)</li>
					<li><a href="/stream-large-file">/stream-large-file</a></li>
					<li><a href="/stats">/stats</a></li>
					<li><a href="/timeout">/timeout</a></li>
					<li><a href="/cached">/cached</a></li>
					<li><a href="/crash">/crash</a></li>
					<li><a href="/fail">/fail</a></li>
					<li><a href="/stop?method=post">/stop</a></li>
				</ul>
			</body>
		</html>

	// we prepend 2048 "empty" bytes to push the browser to immediately start displaying the incoming chunks
	lazy val streamStart = " " * 2048 + "<html><body><h2>A streaming response</h2><p>(for 15 seconds)<ul>"
	lazy val streamEnd = "</ul><p>Finished.</p></body></html>"

	def simpleStringStream: Stream[String] = {
		val secondStream = Stream.continually {
			// CAUTION: we block here to delay the stream generation for you to be able to follow it in your browser,
			// this is only done for the purpose of this demo, blocking in actor code should otherwise be avoided
			Thread.sleep(500)
			"<li>" + DateTime.now.toIsoDateTimeString + "</li>"
		}
		streamStart #:: secondStream.take(15) #::: streamEnd #:: Stream.empty
	}

	// simple case class whose instances we use as send confirmation message for streaming chunks
	case class Ok(remaining: Int)

	def sendStreamingResponse(ctx: RequestContext): Unit =
		actorRefFactory.actorOf {
			Props {
				new Actor with SprayActorLogging {
					// we use the successful sending of a chunk as trigger for scheduling the next chunk
					val responseStart = HttpResponse(entity = HttpEntity(`text/html`, streamStart))
					ctx.responder ! ChunkedResponseStart(responseStart).withAck(Ok(16))

					def receive = {
						case Ok(0) =>
							ctx.responder ! MessageChunk(streamEnd)
							ctx.responder ! ChunkedMessageEnd()
							context.stop(self)

						case Ok(remaining) =>
							in(500.millis) {
								val nextChunk = MessageChunk("<li>" + DateTime.now.toIsoDateTimeString + "</li>")
								ctx.responder ! nextChunk.withAck(Ok(remaining - 1))
							}

						case ev: Http.ConnectionClosed =>
							log.warning("Stopping response streaming due to {}", ev)
					}
				}
			}
		}

	implicit val statsMarshaller: Marshaller[Stats] =
		Marshaller.delegate[Stats, String](ContentType.`text/plain`) { stats =>
			"Uptime                : " + stats.uptime.formatHMS + '\n' +
			"Total requests        : " + stats.totalRequests + '\n' +
			"Open requests         : " + stats.openRequests + '\n' +
			"Max open requests     : " + stats.maxOpenRequests + '\n' +
			"Total connections     : " + stats.totalConnections + '\n' +
			"Open connections      : " + stats.openConnections + '\n' +
			"Max open connections  : " + stats.maxOpenConnections + '\n' +
			"Requests timed out    : " + stats.requestTimeouts + '\n'
		}

	lazy val largeTempFile: File = {
		val file = File.createTempFile("streamingTest", ".txt")
		FileUtils.writeAllText((1 to 1000) map ("This is line " + _) mkString "\n", file)
		file.deleteOnExit()
		file
	}

	def in[U](duration: FiniteDuration)(body: => U): Unit =
		actorSystem.scheduler.scheduleOnce(duration)(body)
}
