package viscel

import akka.actor.{ ActorSystem, Props, Actor }
import akka.io.IO
import akka.util.Timeout
import com.typesafe.scalalogging.slf4j.Logging
import org.jsoup.Jsoup
import org.rogach.scallop._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }
import spray.can.Http
import spray.client.pipelining._
import spray.http.Uri
import java.io.File
import viscel.core._
import viscel.store._

import spray.io.ServerSSLEngineProvider
import java.security.{ SecureRandom, KeyStore }
import javax.net.ssl.{ KeyManagerFactory, SSLContext, TrustManagerFactory }

object Viscel {

	// implicit val myEngineProvider = ServerSSLEngineProvider { engine =>
	//  // engine.setEnabledCipherSuites(Array("TLS_RSA_WITH_AES_256_CBC_SHA"))
	//  // engine.setEnabledProtocols(Array("SSLv3", "TLSv1"))
	//  engine
	// }

	// implicit def sslContext: SSLContext = {
	//  val keyStoreResource = "/ssl-test-keystore.jks"
	//  val password = ""

	//  val keyStore = KeyStore.getInstance("jks")
	//  keyStore.load(getClass.getResourceAsStream(keyStoreResource), password.toCharArray)
	//  val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
	//  keyManagerFactory.init(keyStore, password.toCharArray)
	//  val trustManagerFactory = TrustManagerFactory.getInstance("SunX509")
	//  trustManagerFactory.init(keyStore)
	//  val context = SSLContext.getInstance("TLS")
	//  context.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, new SecureRandom)
	//  context
	// }

	def main(args: Array[String]) {

		val conf = new Conf(args)

		System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, conf.loglevel())
		System.setProperty(org.slf4j.impl.SimpleLogger.SHOW_THREAD_NAME_KEY, "false")

		sys.addShutdownHook { Neo.shutdown() }

		if (conf.dbwarmup()) time("warmup db") { Neo.txs {} }

		if (conf.createIndexes()) {
			Neo.execute("create index on :Collection(id)")
			//Neo.execute("create index on :Element(position)")
			//Neo.execute("create index on :User(name)")
		}

		if (conf.purgeUnreferenced()) {
			Neo.execute("""
				|match (user :User), (col: Collection)
				|where NOT( user -[:bookmarked]-> () <-[:bookmark]- col )
				|with col
				|match col -[r1]-> (node) -[r2?]- ()
				|delete node, r1, r2
				""").dumpToString.pipe { println }
		}

		conf.importdb.get.foreach(dbdir => new tools.LegacyImporter(dbdir).importAll)

		for {
			userdir <- conf.importbookmarks.get
			uname <- conf.username.get
			un <- UserNode(uname)
		} yield { tools.BookmarkImporter(un, userdir) }

		implicit val system = ActorSystem()
		val ioHttp = IO(Http)

		if (conf.server()) {
			val server = system.actorOf(Props[viscel.server.Server], "viscel-server")
			ioHttp ! Http.Bind(server, interface = "0", port = conf.port())
		}

		if (conf.core()) {
			val pipe = {
				implicit val timeout: Timeout = 30.seconds
				sendReceive(ioHttp)
			}
			val clock = new Clockwork(pipe)
			clock.test
		}

		if (conf.dbshutdown()) Neo.shutdown
		if (conf.actorshutdown()) system.shutdown
	}

}

class Conf(arguments: Seq[String]) extends ScallopConf(arguments) {
	version("viscel 5")
	banner("")
	val loglevel = opt[String](default = Option("INFO"), descr = "set the default loglevel")
	val port = opt[Int](default = Some(8080), descr = "server listening port")
	val server = toggle(default = Some(true), descrYes = "start the server")
	val core = toggle(default = Some(true), descrYes = "start the core downloader")
	val dbwarmup = toggle(default = Some(true), descrYes = "do database warmup")
	val dbshutdown = toggle(default = Some(false), descrYes = "shut the database down when main finishes")
	val actorshutdown = toggle(default = Some(false), descrYes = "shut the actor system down when main finishes")
	val importdb = opt[String](descr = "path to collections.db")
	val importbookmarks = opt[String](descr = "path to user.ini")
	val createIndexes = opt[Boolean](descr = "create neo4j indexes")
	val username = opt[String](descr = "username to work with")
	val purgeUnreferenced = opt[Boolean](descr = "purge unreferenced collections from database")

	dependsOnAny(importbookmarks, List(username))

	errorMessageHandler = { message =>
		printHelp
		println()
		println(s"Error: $message")
		sys.exit(1)
	}
}
