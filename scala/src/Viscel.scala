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
	implicit val system = ActorSystem()

	// create and start our service actor
	val server = system.actorOf(Props[Server], "viscel-server")
	val pipe = sendReceive

	IO(Http) ! Http.Bind(server, interface = "0", port = 8080)

	val cE = new core.Experimental

	get(cE.first.toString)

	def get(uri: String) {
		val res = for {
			res <- pipe(Get(uri))
			document = Jsoup.parse(res.entity.asString, cE.first.toString)
		} yield {
			cE.mount(document)
		}

		res foreach {case (next, img) => get(next)} 
	}



}
