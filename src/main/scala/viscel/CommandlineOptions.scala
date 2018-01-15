package viscel

import joptsimple.{BuiltinHelpFormatter, OptionException, OptionParser, OptionSet, OptionSpec, OptionSpecBuilder}

import scala.language.implicitConversions


object CommandlineOptions extends OptionParser {
	//	val loglevel = accepts("loglevel", "set the loglevel")
	//		.withRequiredArg().describedAs("loglevel").defaultsTo("INFO")
	val port = accepts("port", "server listening port")
		.withRequiredArg().ofType(Predef.classOf[Int]).defaultsTo(2358).describedAs("port")
	val interface = accepts("interface", "Interface to bind the server on.")
		.withRequiredArg().ofType(Predef.classOf[String]).defaultsTo("0").describedAs("interface")
	val noserver = accepts("noserver", "do not start the server")
	val nocore = accepts("nocore", "do not start the core downloader")
	val optBasedir = accepts("basedir", "the base working directory")
		.withRequiredArg().ofType(Predef.classOf[String]).defaultsTo("./data/").describedAs("basedir")
	val nodbwarmup = accepts("nodbwarmup", "skip database warmup")
	val shutdown = accepts("shutdown", "shutdown after main")
	val help = accepts("help").forHelp()
	val upgrade = accepts("upgradedb", "upgrade database and folder layout from version 2 to version 3")
	val cleanblobs = accepts("cleanblobs", "cleans blobs from blobstore which are no longer linked")
	val optBlobdir = accepts("blobdir", "directory to store blobs (the images). Can be absolute, otherwise relative to basedir")
		.withRequiredArg().ofType(Predef.classOf[String]).defaultsTo("./blobs/").describedAs("blobdir")

	implicit def optToBool(opt: OptionSpecBuilder)(implicit oset: OptionSet): Boolean = oset.has(opt)

	implicit class OptEnhancer[T](opt: OptionSpec[T]) {
		def ?(implicit oset: OptionSet): Boolean = oset.has(opt)
		def get(implicit oset: OptionSet): Option[T] = if (!oset.has(opt)) None else Some(apply())
		def apply()(implicit oset: OptionSet): T = oset.valueOf(opt)
	}

	def parseOptions(args: Seq[String]): OptionSet = {
		val formatWidth = try {new jline.console.ConsoleReader().getTerminal.getWidth}
		catch {case e: Throwable => 80}
		formatHelpWith(new BuiltinHelpFormatter(formatWidth, 4))
		try {
			parse(args: _*)
		}
		catch {
			case oe: OptionException =>
				printHelpOn(System.out)
				Console.println()
				Console.println(s"$oe")
				sys.exit(0)
		}
	}

}
