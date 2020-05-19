package visceljs.connection

import org.scalajs.dom
import rescala.default.{Events, Signal, _}
import viscel.shared.Log

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.util.{Failure, Success}


object ServiceWorker {
  def register(): Signal[String] = {
    Events.fromCallback[String] { cb =>
      Option(dom.experimental.serviceworkers.toServiceWorkerNavigator(dom.window.navigator).serviceWorker) match {
        case None                => cb("none")
        case Some(serviceworker) =>
          serviceworker.register("serviceworker.js").toFuture.onComplete {
            case Success(registration) =>
              cb("registered")
              registration.addEventListener("updatefound", (event: js.Any) => {
                val newWorker = registration.installing
                newWorker.addEventListener("statechange", (event: js.Any) => {
                  cb(newWorker.state)
                })
              })
            case Failure(error)        =>
              Log.JS.error(s"serviceworker failed", error)
              cb("failed")
          }
      }
    }.event.latest("init")
  }
}
