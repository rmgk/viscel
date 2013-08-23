package viscel

import akka.actor.{ ActorSystem, Props, Actor }
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

import spray.io.ServerSSLEngineProvider
import java.security.{ SecureRandom, KeyStore }
import javax.net.ssl.{ KeyManagerFactory, SSLContext, TrustManagerFactory }

object Viscel {

	implicit val system = ActorSystem()

	val ioHttp = IO(Http)

	// val pipe = {
	// 	implicit val timeout: Timeout = 30.seconds
	// 	sendReceive(ioHttp)
	// }

	// val cW = new Clockwork(pipe)

	implicit val myEngineProvider = ServerSSLEngineProvider { engine =>
		// engine.setEnabledCipherSuites(Array("TLS_RSA_WITH_AES_256_CBC_SHA"))
		// engine.setEnabledProtocols(Array("SSLv3", "TLSv1"))
		engine
	}

	implicit def sslContext: SSLContext = {
		val keyStoreResource = "/ssl-test-keystore.jks"
		val password = ""

		val keyStore = KeyStore.getInstance("jks")
		keyStore.load(getClass.getResourceAsStream(keyStoreResource), password.toCharArray)
		val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
		keyManagerFactory.init(keyStore, password.toCharArray)
		val trustManagerFactory = TrustManagerFactory.getInstance("SunX509")
		trustManagerFactory.init(keyStore)
		val context = SSLContext.getInstance("TLS")
		context.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, new SecureRandom)
		context
	}

	def main(args: Array[String]) {
		System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "INFO")
		System.setProperty(org.slf4j.impl.SimpleLogger.SHOW_THREAD_NAME_KEY, "false")

		sys.addShutdownHook {
			system.shutdown()
			store.Neo.shutdown()
		}

		time("warmup db") { store.Neo.txs {} }

		// store.Neo.execute("create index on :Collection(id)")
		// store.Neo.execute("create index on :Element(position)")
		// store.Neo.execute("create index on :User(name)")

		val server = system.actorOf(Props[viscel.server.Server], "viscel-server")
		ioHttp ! Http.Bind(server, interface = "0", port = 8080)
	}

}
