package viscel

import akka.actor.{ActorSystem, Props, Actor}
import akka.io.IO
import spray.can.Http


object Viscel extends App {
	implicit val system = ActorSystem()

	// create and start our service actor
	val server = system.actorOf(Props[Server], "viscel-server")

	IO(Http) ! Http.Bind(server, interface = "0", port = 8080)

}
