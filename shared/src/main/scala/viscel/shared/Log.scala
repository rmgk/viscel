package viscel.shared

import de.rmgk.logging.{Level, Logger}

object Log {
  val common: Logger = de.rmgk.logging.Logger(tag = "", level = Level.Info, tracing = false)
  val Tool: Logger = common.copy(tag = "Tool: ")
  val Main: Logger = common
  val Devel: Logger = common.copy(tag = "Devel: ", level = Level.Error, tracing = true)
  val Narrate: Logger = common.copy(tag = "Narrate: ")
  val Web: Logger = common.copy(tag = "Web: ")
  val Store: Logger = common.copy(tag = "IO: ")
  val Scribe: Logger = common.copy(tag = "Scribe: ")
  val Server: Logger = common.copy(tag = "Serv: ")
}
