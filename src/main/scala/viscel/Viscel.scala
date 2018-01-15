package viscel

import joptsimple.OptionSet
import viscel.CommandlineOptions._
import viscel.shared.Log

object Viscel {

	def main(args: Array[String]): Unit = run(args: _*)

	def run(args: String*): Services = {
		implicit val conf: OptionSet = parseOptions(args)

		if (help.? || conf.nonOptionArguments.size > 0) {
			printHelpOn(System.out)
			sys.exit(0)
		}

		val services = new Services(optBasedir(), optBlobdir(), interface(), port())

		if (cleanblobs.?) {
			services.replUtil.cleanBlobDirectory()
		}

		if (!noserver.?) {
			services.startServer()
		}

		if (!nocore.?) {
			services.clockwork.recheckPeriodically()
			services.activateNarrationHint()
		}

		if (shutdown.?) {
			services.terminateServer()
		}
		Log.info("initialization done")
		services
	}
}

