package viscel

import java.io.File
import java.util.concurrent.{TimeUnit, _}

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import com.typesafe.scalalogging.slf4j.StrictLogging
import joptsimple._
import org.neo4j.graphdb.traversal._
import org.neo4j.visualization.graphviz._
import org.neo4j.walk.Walker
import org.scalactic.TypeCheckedTripleEquals._
import spray.can.Http
import spray.client.pipelining
import spray.http.HttpEncodings
import viscel.crawler.Clockwork
import viscel.database.{NeoSingleton, rel}
import viscel.server.Server
import viscel.store._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.util.{Failure, Success}

object Viscel extends StrictLogging {

	def main(args: Array[String]): Unit = run(args: _*)

	def run(args: String*) = {
		import viscel.Opts._
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

		sys.addShutdownHook { NeoSingleton.shutdown() }

		if (!nodbwarmup.?) time("warmup db") { NeoSingleton.txs {} }

		val configNode = NeoSingleton.tx { ntx => Config.get()(ntx) }

		logger.info(s"config version: ${ NeoSingleton.tx { ntx => configNode.version(ntx) } }")

		if (createIndexes.?) {
			NeoSingleton.execute("create index on :Collection(id)")
			NeoSingleton.execute("create index on :Blob(source)")
		}

		for {
			dotpath <- makedot.get
			cid <- collectionid.get
			cn <- Collection.find(cid)(NeoSingleton)
		} { visualizeCollection(cn, dotpath) }

		implicit val system = ActorSystem()

		val ioHttp = IO(Http)
		val iopipe = pipelining.sendReceive(ioHttp)(system.dispatcher, 300.seconds)

		if (!noserver.?) {
			val server = system.actorOf(Props(Predef.classOf[Server], NeoSingleton), "viscel-server")
			ioHttp ! Http.Bind(server, interface = "0", port = port())
		}

		if (!nocore.?) {
			Clockwork.handleHints(
				Deeds.uiCoin,
				ExecutionContext.fromExecutor(new ThreadPoolExecutor(
					0, 1, 1L, TimeUnit.SECONDS, new LinkedBlockingQueue[Runnable])),
				iopipe,
				NeoSingleton)
		}

		if (shutdown.?) {
			NeoSingleton.shutdown()
			system.shutdown()
		}


		Deeds.responses += {
			case Success(res) => NeoSingleton.tx { ntx =>
				configNode.download(
					size = res.entity.data.length,
					success = res.status.isSuccess,
					compressed = res.encoding === HttpEncodings.deflate || res.encoding === HttpEncodings.gzip)(ntx)
			}
			case Failure(_) => NeoSingleton.tx { ntx => configNode.download(0, success = false)(ntx) }
		}

		(system, ioHttp, iopipe)
	}


	def visualizeCollection(col: Collection, dotpath: String) = {
		NeoSingleton.txs {
			val td = NeoSingleton.db.traversalDescription().depthFirst
				.relationships(rel.narc)
				.relationships(rel.describes)
				.evaluator(Evaluators.all)
			val writer = new GraphvizWriter()
			writer.emit(new File(dotpath), Walker.crosscut(td.traverse(col.self).nodes, rel.narc, rel.describes))
		}
	}

}

object Opts extends OptionParser {
//	val loglevel = accepts("loglevel", "set the loglevel")
//		.withRequiredArg().describedAs("loglevel").defaultsTo("INFO")
	val port = accepts("port", "server listening port")
		.withRequiredArg().ofType(Predef.classOf[Int]).defaultsTo(4242).describedAs("port")
	val noserver = accepts("noserver", "do not start the server")
	val nocore = accepts("nocore", "do not start the core downloader")
	val nodbwarmup = accepts("nodbwarmup", "skip database warmup")
	val shutdown = accepts("shutdown", "shutdown after main")
	val createIndexes = accepts("create-indexes", "create database indexes")
//	val purgeUnreferenced = accepts("purge-unreferenced", "remove entries that are not referenced by any user")
	val makedot = accepts("makedot", "makes a dot file for a given collection")
		.withRequiredArg().describedAs("path")
	val collectionid = accepts("collectionid", "id of the ccollection for other commands")
		.withRequiredArg().describedAs("collection id")
	val help = accepts("help").forHelp()

	implicit def optToBool(opt: OptionSpecBuilder)(implicit oset: OptionSet): Boolean = oset.has(opt)

	implicit class OptEnhancer[T](opt: OptionSpec[T]) {
		def ?(implicit oset: OptionSet): Boolean = oset.has(opt)
		def get(implicit oset: OptionSet): Option[T] = if (!oset.has(opt)) None else Some(apply())
		def apply()(implicit oset: OptionSet): T = oset.valueOf(opt)
	}

}
