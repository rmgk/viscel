package visceljs.connection

import org.scalajs.dom
import org.scalajs.dom.ServiceWorkerContainer
import rescala.default.{Events, Signal, _}
import viscel.shared.Log

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.timers._
import scala.util.{Failure, Success}

object ServiceWorker {

  def getDefined[T <: AnyRef](ts: T*): Option[T] = ts.find(v => v != null && !scalajs.js.isUndefined(v))

  val serviceWorkerOption: Option[ServiceWorkerContainer] = {
    val workerSupported_? = dom.window.navigator.serviceWorker
    getDefined(workerSupported_?)
  }

  def register(): Signal[String] = {
    transaction() { implicit at =>
      Events.fromCallback[String] { cb =>
        serviceWorkerOption match {
          case None =>
            // this is a bit sad, we can not call cb within the starting transaction,
            // as that forces a new transaction …
            setTimeout(0) { cb("none") }
          case Some(serviceworker) =>
            serviceworker.register("serviceworker.js").toFuture.onComplete {
              case Success(registration) =>
                cb("registered")
                registration.addEventListener(
                  "updatefound",
                  (_: js.Any) => {
                    val newWorker = registration.installing
                    newWorker.addEventListener(
                      "statechange",
                      (_: js.Any) => {
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

  def unregister(): Future[List[Boolean]] = {
    scribe.warn("trying to unregister service worker")
    val res = serviceWorkerOption.map { swc =>
      swc.getRegistrations().toFuture.flatMap { registrations =>
        Future.sequence(registrations.toList.map { sw =>
          sw.unregister().toFuture
        })
      }
    }.getOrElse(Future.successful(List()))

    res.onComplete {
      case Failure(error) =>
        Log.JS.error(s"could not unregister serviceworker", error)
      case _ =>
    }

    res
  }
}
