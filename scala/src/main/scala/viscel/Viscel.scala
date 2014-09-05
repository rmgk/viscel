package viscel

import java.io.File

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import com.typesafe.scalalogging.slf4j.StrictLogging
import joptsimple._
import org.neo4j.graphdb.traversal._
import org.neo4j.visualization.graphviz._
import org.neo4j.walk.Walker
import spray.can.Http
import viscel.core._
import viscel.store._

import scala.language.implicitConversions

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

		//System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, loglevel())
		//System.setProperty(org.slf4j.impl.SimpleLogger.SHOW_THREAD_NAME_KEY, "false")
		//System.setProperty(org.slf4j.impl.SimpleLogger.SHOW_LOG_NAME_KEY, "false")

		sys.addShutdownHook { Neo.shutdown() }

		if (!nodbwarmup.?) time("warmup db") { Neo.txs {} }
		logger.info(s"config version: ${ ConfigNode().version }")

		if (createIndexes.?) {
			Neo.execute("create index on :Collection(id)")
			//Neo.execute("create index on :Element(position)")
			Neo.execute("create index on :User(name)")
			Neo.execute("create index on :Blob(source)")
		}

		if (purgeUnreferenced.?) {
			viscel.store.Util.purgeUnreferenced()
		}

		//		importdb.get.foreach(dbdir => new tools.LegacyImporter(dbdir.toString).importAll)

		//		for {
		//			userpath <- importbookmarks.get
		//			uname <- username.get
		//			un <- UserNode(uname)
		//		} { tools.BookmarkImporter(un, userpath.toString) }

		for {
			dotpath <- makedot.get
			uname <- username.get
			un <- UserNode(uname)
		} { visualizeUser(un, dotpath) }

		for {
			dotpath <- makedot.get
			cid <- collectionid.get
			cn <- CollectionNode(cid)
		} { visualizeCollection(cn, dotpath) }
		implicit val system = ActorSystem()

		val ioHttp = IO(Http)

		val props = Props(Predef.classOf[Clockwork], ioHttp)
		val clockwork = system.actorOf(props, "clockwork")

		if (!noserver.?) {
			val server = system.actorOf(Props[viscel.server.Server], "viscel-server")
			ioHttp ! Http.Bind(server, interface = "0", port = port())
		}

		if (!nocore.?) {
			//clockwork ! Clockwork.EnqueueDefault
		}

		if (shutdown.?) {
			Neo.shutdown()
			system.shutdown()
		}
		(system, ioHttp, clockwork)
	}

	def visualizeUser(user: UserNode, dotpath: String) = {
		Neo.tx { db =>
			val td = db.traversalDescription().depthFirst
			.relationships(rel.bookmarked)
			.relationships(rel.bookmarks)
			.relationships(rel.bookmark)
			.relationships(rel.first)
			.relationships(rel.last)
			.evaluator(Evaluators.excludeStartPosition)
			val writer = new GraphvizWriter()
			writer.emit(new File(dotpath), Walker.crosscut(td.traverse(user.self).nodes, rel.bookmarked, rel.bookmarks, rel.bookmark, rel.first, rel.last))
		}
	}

	def visualizeCollection(col: CollectionNode, dotpath: String) = {
		Neo.tx { db =>
			val td = db.traversalDescription().depthFirst
			.relationships(rel.first)
			.relationships(rel.last)
			.relationships(rel.skip)
			.relationships(rel.archive)
			.relationships(rel.describes)
			.relationships(rel.parent)
			.evaluator(Evaluators.all)
			val writer = new GraphvizWriter()
			writer.emit(new File(dotpath), Walker.crosscut(td.traverse(col.self).nodes, rel.first, rel.last, rel.skip, rel.archive, rel.describes, rel.parent))
		}
	}

}

object Opts extends OptionParser {
	val loglevel = accepts("loglevel", "set the loglevel")
		.withRequiredArg().describedAs("loglevel").defaultsTo("INFO")
	val port = accepts("port", "server listening port")
		.withRequiredArg().ofType(Predef.classOf[Int]).defaultsTo(8080).describedAs("port")
	val noserver = accepts("noserver", "do not start the server")
	val nocore = accepts("nocore", "do not start the core downloader")
	val nodbwarmup = accepts("nodbwarmup", "skip database warmup")
	val shutdown = accepts("shutdown", "shutdown after main")
	val importdb = accepts("importdb", "import a viscel 4 database")
		.withRequiredArg().ofType(Predef.classOf[File]).describedAs("data/collections.db")
	val importbookmarks = accepts("importbookmarks", "imports viscel 4 bookmark file for given username")
		.withRequiredArg().ofType(Predef.classOf[File]).describedAs("user/user.ini")
	val createIndexes = accepts("create-indexes", "create database indexes")
	val username = accepts("username", "name of the user for other commands")
		.requiredIf("importbookmarks").withRequiredArg().describedAs("name")
	val purgeUnreferenced = accepts("purge-unreferenced", "remove entries that are not referenced by any user")
	val makedot = accepts("makedot", "makes a dot file for a given user or collection")
		.withRequiredArg().describedAs("path")
	val collectionid = accepts("collectionid", "id of the ccollection for other commands")
		.withRequiredArg().describedAs("collection id")
	val help = accepts("help").forHelp()

	implicit def optToBool(opt: OptionSpecBuilder)(implicit oset: OptionSet): Boolean = oset.has(opt)

	implicit class OptEnhancer[T](opt: OptionSpec[T]) {
		def ?(implicit oset: OptionSet): Boolean = oset.has(opt)
		def get(implicit oset: OptionSet): Option[T] = if (! ?) None else Some(apply())
		def apply()(implicit oset: OptionSet): T = oset.valueOf(opt)
	}

}
