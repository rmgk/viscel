package viscel.shared

import de.rmgk.logging.{Level, Logger}

object Log {

  val common: Logger = de.rmgk.logging.Logger(tag = "", level = Level.Trace, tracing = true)
  val Tool: Logger = common.copy(tag = "Tool: ")
  val Main: Logger = common
  val Web: Logger = common.copy(tag = "Web: ")
  val Store: Logger = common.copy(tag = "IO: ")
  val Scribe: Logger = common.copy(tag = "Scribe: ")
  val Server: Logger = common.copy(tag = "Serv: ")
}
