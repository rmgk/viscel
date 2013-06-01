package viscel

import akka.actor.{ActorSystem, Props, Actor}
import akka.io.IO
import spray.can.Http
import spray.client.pipelining._
import scala.concurrent._
import ExecutionContext.Implicits.global


object Viscel extends App {
	implicit val system = ActorSystem()

	// create and start our service actor
	val server = system.actorOf(Props[Server], "viscel-server")
	val pipe = sendReceive

	IO(Http) ! Http.Bind(server, interface = "0", port = 8080)

	val cE = new core.Experimental(pipe)
	// cE.doSomething

}
