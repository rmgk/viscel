package viscel

import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.{LinkedBlockingQueue, ThreadPoolExecutor, TimeUnit}

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import joptsimple.{BuiltinHelpFormatter, OptionException, OptionParser, OptionSet, OptionSpec, OptionSpecBuilder}
import org.scalactic.TypeCheckedTripleEquals._
import spray.can.Http
import spray.client.pipelining
import spray.client.pipelining.SendReceive
import spray.http.HttpEncodings
import viscel.crawler.Clockwork
import viscel.database.{NeoInstance, label}
import viscel.scribe.Scribe
import viscel.server.Server
import viscel.store.Config

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.util.{Failure, Success}

object Viscel {

	def time[T](desc: String = "")(f: => T): T = {
		val start = System.nanoTime
		val res = f
		Console.println(s"$desc took ${ (System.nanoTime - start) / 1000000.0 } ms")
		res
	}

	var basepath: Path = _

	def main(args: Array[String]): Unit = run(args: _*)

	def run(args: String*): (ActorSystem, Scribe) = {
		import viscel.Viscel.Options._
		val formatWidth = try { new jline.console.ConsoleReader().getTerminal.getWidth }
		catch { case e: Throwable => 80 }
		formatHelpWith(new BuiltinHelpFormatter(formatWidth, 4))
		implicit val conf = try {
			parse(args: _*)
		}
		catch {
			case oe: OptionException =>
				printHelpOn(System.out)
				Console.println()
				Console.println(s"$oe")
				sys.exit(0)
		}

		if (help.? || conf.nonOptionArguments.size > 0) {
			printHelpOn(System.out)
			sys.exit(0)
		}

		basepath = Paths.get(basedir())
		Files.createDirectories(basepath)

		val system = ActorSystem()

		val scribe = viscel.scribe.Scribe(basepath.resolve("scribe"), system,
			ExecutionContext.fromExecutor(new ThreadPoolExecutor(
				0, 1, 1L, TimeUnit.SECONDS, new LinkedBlockingQueue[Runnable])))


		if (upgradedb.?) {
			val neo = new NeoInstance(basepath.resolve(dbpath()).toString)
			Upgrader.doUpgrade(scribe, neo)
			neo.shutdown()
		}


		if (!noserver.?) {
			val server = system.actorOf(Props(Predef.classOf[Server], scribe), "viscel-server")
			IO(Http)(system) ! Http.Bind(server, interface = "0", port = port())
		}

//		if (!nocore.?) {
//			val clockworkContext = ExecutionContext.fromExecutor(new ThreadPoolExecutor(
//				0, 1, 1L, TimeUnit.SECONDS, new LinkedBlockingQueue[Runnable]))
//
//			Deeds.narratorHint = Clockwork.handleHints(clockworkContext, iopipe, neo)
//			Clockwork.recheckPeriodically(clockworkContext, iopipe, neo)
//
//			Deeds.jobResult = {
//				case messages@_ :: _ => Log.error(s"some job failed: $messages")
//				case Nil =>
//			}
//		}

		if (shutdown.?) {
			scribe.neo.shutdown()
			system.shutdown()
		}

		Log.info("initialization done")

		(system, scribe)
	}


	object Options extends OptionParser {
		//	val loglevel = accepts("loglevel", "set the loglevel")
		//		.withRequiredArg().describedAs("loglevel").defaultsTo("INFO")
		val port = accepts("port", "server listening port")
			.withRequiredArg().ofType(Predef.classOf[Int]).defaultsTo(2358).describedAs("port")
		val noserver = accepts("noserver", "do not start the server")
		val nocore = accepts("nocore", "do not start the core downloader")
		val basedir = accepts("basedir", "die base working directory")
			.withRequiredArg().ofType(Predef.classOf[String]).defaultsTo(".").describedAs("basedir")
		val dbpath = accepts("dbpath", "path of the neo database")
			.withRequiredArg().ofType(Predef.classOf[String]).defaultsTo("store").describedAs("dbpath")
		val nodbwarmup = accepts("nodbwarmup", "skip database warmup")
		val shutdown = accepts("shutdown", "shutdown after main")
		val help = accepts("help").forHelp()
		val upgradedb = accepts("upgradedb", "upgrade the db from version 1 to version 2")

		implicit def optToBool(opt: OptionSpecBuilder)(implicit oset: OptionSet): Boolean = oset.has(opt)

		implicit class OptEnhancer[T](opt: OptionSpec[T]) {
			def ?(implicit oset: OptionSet): Boolean = oset.has(opt)
			def get(implicit oset: OptionSet): Option[T] = if (!oset.has(opt)) None else Some(apply())
			def apply()(implicit oset: OptionSet): T = oset.valueOf(opt)
		}

	}


}

