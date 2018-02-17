package vitzen.logging

import vitzen.logging.Level._

sealed class Level(val value: Int, val name: String)
object Level {
  object Trace extends Level(0, "Trace")
  object Debug extends Level(1, "Debug")
  object Info extends Level(2, "Info ")
  object Warn extends Level(3, "Warn ")
  object Error extends Level(4, "Error")
}

case class Logger(tag: String = "",
                  level: Level = Trace,
                  tracing: Boolean = true,
                 ) {
  @inline final def trace(m: => Any)(implicit c: Context): Unit = log(Trace, m)
  @inline final def debug(m: => Any)(implicit c: Context): Unit = log(Trace, m)
  @inline final def info(m: => Any)(implicit c: Context): Unit = log(Trace, m)
  @inline final def warn(m: => Any)(implicit c: Context): Unit = log(Trace, m)
  @inline final def error(m: => Any)(implicit c: Context): Unit = log(Trace, m)

  @inline final def tracemsg(implicit c: Context): String = {
    if (tracing) {
      val fstring = c.file.value
      val last = fstring.lastIndexOf('/') + 1
      s".(${fstring.substring(last)}:${c.line.value})"
    }
    else ""
  }

  @inline final def log(l: Level, m: => Any)(implicit c: Context): Unit =
    if (l.value >= level.value) Console.println(s"$tag$m$tracemsg")
}

case class Context(file: sourcecode.File, line: sourcecode.Line)
object Context {
  @inline implicit def fromImplicit(implicit file: sourcecode.File, line: sourcecode.Line): Context = Context(file, line)
}
