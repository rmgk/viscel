package viscel.shared

import scribe.Logger

object Log {

  import scribe.format._
  val develFormatter: Formatter = formatter"$message$mdc [$positionAbbreviated]"
  val normalFormatter: Formatter = formatter"$message$mdc [${className.abbreviate(maxLength = 15, padded = false)}]"
  Logger.root.clearHandlers().withHandler(formatter = normalFormatter,
                                          minimumLevel = Some(scribe.Level.Info)).replace()

  val Tool: Logger = Logger("Tool")
  val Main: Logger = Logger.root
  val Devel: Logger = Logger("Devel").withHandler(minimumLevel = Some(scribe.Level.Trace),
                                                  formatter = develFormatter)
  val Narrate: Logger = Logger("Narrate")
  val JS: Logger = Logger("JS")
  val Crawl: Logger = Logger("CW")
  val Store: Logger = Logger("IO")
  val Scribe: Logger = Logger("Scribe")
  val Server: Logger = Logger("Serv")
}
