package viscel

import akka.actor.{ActorSystem, Props, Actor}
import akka.io.IO
import spray.can.Http
import spray.client.pipelining._
import scala.concurrent._
import ExecutionContext.Implicits.global
import spray.client.pipelining._
import org.htmlcleaner._
import scala.concurrent._
import scala.concurrent.duration._
import ExecutionContext.Implicits.global
import spray.http.Uri
import com.typesafe.scalalogging.slf4j.Logging
import org.jsoup.Jsoup

object Viscel extends App {

	System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "INFO");
	System.setProperty(org.slf4j.impl.SimpleLogger.SHOW_THREAD_NAME_KEY, "false");


	Storage.init()
	implicit val system = ActorSystem()

	// create and start our service actor
	val pipe = sendReceive

	val cW = new Clockwork(pipe)

	val server = system.actorOf(Props[Server], "viscel-server")
	IO(Http) ! Http.Bind(server, interface = "0", port = 8080)


}
