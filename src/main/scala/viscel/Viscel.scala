package viscel

import java.nio.file.{Paths, Path}
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

	var neo: NeoInstance = _
	var iopipe: SendReceive = _

	var basepath: Path = _

	def main(args: Array[String]): Unit = run(args: _*)

	def run(args: String*) = {
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

		neo = new NeoInstance(basepath.resolve(dbpath()).toString)

		if (!nodbwarmup.?) time("warmup db") { neo.txs {} }

		neo.txs {
			if (!neo.db.schema().getConstraints(label.Collection).iterator().hasNext)
				neo.db.schema().constraintFor(label.Collection).assertPropertyIsUnique("id").create()
		}

		val configNode = neo.tx { ntx => Config.get()(ntx) }

		Log.info(s"config version: ${ neo.tx { ntx => configNode.version(ntx) } }")

		implicit val system = ActorSystem()

		val ioHttp = IO(Http)
		iopipe = pipelining.sendReceive(ioHttp)(system.dispatcher, 300.seconds)

		if (!noserver.?) {
			val server = system.actorOf(Props(Predef.classOf[Server], neo), "viscel-server")
			ioHttp ! Http.Bind(server, interface = "0", port = port())
		}

		if (!nocore.?) {
			val clockworkContext = ExecutionContext.fromExecutor(new ThreadPoolExecutor(
				0, 1, 1L, TimeUnit.SECONDS, new LinkedBlockingQueue[Runnable]))

			Deeds.narratorHint = Clockwork.handleHints(clockworkContext, iopipe, neo)
			Clockwork.recheckPeriodically(clockworkContext, iopipe, neo)

			Deeds.jobResult = {
				case messages@_ :: _ => Log.error(s"some job failed: $messages")
				case Nil =>
			}
		}

		if (shutdown.?) {
			neo.shutdown()
			system.shutdown()
		}


		Deeds.responses = {
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
		val basedir = accepts("basedir", "die base working directory")
			.withRequiredArg().ofType(Predef.classOf[String]).defaultsTo(".").describedAs("basedir")
		val dbpath = accepts("dbpath", "path of the neo database")
			.withRequiredArg().ofType(Predef.classOf[String]).defaultsTo("store").describedAs("dbpath")
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

