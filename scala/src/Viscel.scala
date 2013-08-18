package viscel

import akka.actor.{ActorSystem, Props, Actor}
import akka.io.IO
import akka.util.Timeout
import com.typesafe.scalalogging.slf4j.Logging
import org.htmlcleaner._
import org.jsoup.Jsoup
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }
import spray.can.Http
import spray.client.pipelining._
import spray.http.Uri

object Viscel {

	implicit val system = ActorSystem()

	val ioHttp = IO(Http)

	// val pipe = {
	// 	implicit val timeout: Timeout = 30.seconds
	// 	sendReceive(ioHttp)
	// }

	// val cW = new Clockwork(pipe)

	def main(args: Array[String]) {
		System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "INFO")
		System.setProperty(org.slf4j.impl.SimpleLogger.SHOW_THREAD_NAME_KEY, "false")

		sys.addShutdownHook{
			system.shutdown()
			store.Neo.shutdown()
		}

		val server = system.actorOf(Props[Server], "viscel-server")
		ioHttp ! Http.Bind(server, interface = "0", port = 8080)
	}


}
