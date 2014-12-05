package viscel

import java.util.concurrent.{TimeUnit, ThreadPoolExecutor, LinkedBlockingQueue}

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import joptsimple.{OptionParser, BuiltinHelpFormatter, OptionException, OptionSpecBuilder, OptionSet, OptionSpec}
import org.scalactic.TypeCheckedTripleEquals._
import rescala.propagation.Engines.default
import spray.can.Http
import spray.client.pipelining
import spray.http.HttpEncodings
import viscel.crawler.Clockwork
import viscel.database.{NeoInstance, Neo}
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

	var neo: NeoInstance = new NeoInstance("neoViscelStore")


	def main(args: Array[String]): Unit = run(args: _*)

	def run(args: String*) = {
		import Options._
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

		sys.addShutdownHook { neo.shutdown() }

		if (!nodbwarmup.?) time("warmup db") { neo.txs {} }

		val configNode = neo.tx { ntx => Config.get()(ntx) }

		Log.info(s"config version: ${ neo.tx { ntx => configNode.version(ntx) } }")

		implicit val system = ActorSystem()

		val ioHttp = IO(Http)
		val iopipe = pipelining.sendReceive(ioHttp)(system.dispatcher, 300.seconds)

		if (!noserver.?) {
			val server = system.actorOf(Props(Predef.classOf[Server], neo), "viscel-server")
			ioHttp ! Http.Bind(server, interface = "0", port = port())
		}

		if (!nocore.?) {
			Clockwork.handleHints(
				Deeds.uiCollection,
				ExecutionContext.fromExecutor(new ThreadPoolExecutor(
					0, 1, 1L, TimeUnit.SECONDS, new LinkedBlockingQueue[Runnable])),
				iopipe,
				neo)
			Deeds.jobResult += {
				case messages@_ :: _ => Log.error(s"some job failed: $messages")
				case Nil =>
			}
		}

		if (shutdown.?) {
			neo.shutdown()
			system.shutdown()
		}


		Deeds.responses += {
			case Success(res) => neo.tx { ntx =>
				configNode.download(
					size = res.entity.data.length,
					success = res.status.isSuccess,
					compressed = res.encoding === HttpEncodings.deflate || res.encoding === HttpEncodings.gzip)(ntx)
			}
			case Failure(_) => neo.tx { ntx => configNode.download(0, success = false)(ntx) }
		}

		(system, ioHttp, iopipe)
	}

	

	object Options extends OptionParser {
		//	val loglevel = accepts("loglevel", "set the loglevel")
		//		.withRequiredArg().describedAs("loglevel").defaultsTo("INFO")
		val port = accepts("port", "server listening port")
			.withRequiredArg().ofType(Predef.classOf[Int]).defaultsTo(2358).describedAs("port")
		val noserver = accepts("noserver", "do not start the server")
		val nocore = accepts("nocore", "do not start the core downloader")
		val nodbwarmup = accepts("nodbwarmup", "skip database warmup")
		val shutdown = accepts("shutdown", "shutdown after main")
		val help = accepts("help").forHelp()

		implicit def optToBool(opt: OptionSpecBuilder)(implicit oset: OptionSet): Boolean = oset.has(opt)

		implicit class OptEnhancer[T](opt: OptionSpec[T]) {
			def ?(implicit oset: OptionSet): Boolean = oset.has(opt)
			def get(implicit oset: OptionSet): Option[T] = if (!oset.has(opt)) None else Some(apply())
			def apply()(implicit oset: OptionSet): T = oset.valueOf(opt)
		}

	}


}

