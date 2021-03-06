package visceljs.connection

import org.scalajs.dom
import rescala.default.{Events, Signal, _}
import viscel.shared.Log
import visceljs.Definitions

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.util.{Failure, Success}

import scala.scalajs.js.timers._

object ServiceWorker {
  def register(): Signal[String] = {
    transaction() { implicit at =>
      Events.fromCallback[String] { cb =>
        val workerSupported_? =
          dom.experimental.serviceworkers.toServiceWorkerNavigator(dom.window.navigator).serviceWorker
        Definitions.getDefined(workerSupported_?) match {
          case None =>
            // this is a bit sad, we can not call cb within the starting transactin,
            // as that forces a new transactin …
            setTimeout(0) { cb("none") }
          case Some(serviceworker) =>
            serviceworker.register("serviceworker.js").toFuture.onComplete {
              case Success(registration) =>
                cb("registered")
                registration.addEventListener(
                  "updatefound",
                  (event: js.Any) => {
                    val newWorker = registration.installing
                    newWorker.addEventListener(
                      "statechange",
                      (event: js.Any) => {
                        cb(newWorker.state)
                      }
                    )
                  }
                )
              case Failure(error) =>
                Log.JS.error(s"serviceworker failed", error)
                cb("failed")
            }
        }
      }.event.latest("init")
    }
  }
}
