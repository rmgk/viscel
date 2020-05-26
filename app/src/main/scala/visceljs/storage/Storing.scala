package visceljs.storage

import org.scalajs.dom
import rescala.default.{Signal, implicitScheduler}
import upickle.default._

object Storing {

  def storedAs[A: ReadWriter](key: String, default: => A)(create: A => Signal[A]): Signal[A] = {
    val init: A =
      try {upickle.default.read[A](dom.window.localStorage.getItem(key))}
      catch {case _: Throwable =>
        dom.window.localStorage.removeItem(key)
        default
      }
    val sig     = create(init)
    sig.observe({ ft: A =>
      dom.window.localStorage.setItem(key, upickle.default.write(ft))
    }, fireImmediately = false)
    sig
  }

}
