package visceljs

import org.scalajs.dom
import rescala.{Signals, Var, _}
import retier.communicator.ws.akka.WS
import retier.registry.Registry
import retier.transmitter.RemoteRef
import viscel.shared._

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scalatags.JsDom.implicits.stringFrag
import scalatags.JsDom.tags.div


object ViscelJS {

  val wsUri: String = {
    val wsProtocol = if (dom.document.location.protocol == "https:") "wss" else "ws"
    s"$wsProtocol://${dom.document.location.host}${dom.document.location.pathname}ws"
  }

  def main(args: Array[String]): Unit = {
    dom.document.body.appendChild(div("loading data …").render)
    val registry = new Registry
    val connection: Future[RemoteRef] = registry.request(WS(wsUri))
    connection.foreach { remote =>
      val requestContents = registry.lookup(Bindings.contents, remote)
      val hint: (Description, Boolean) => Unit =
        (d, h) => registry.lookup(Bindings.hint, remote).apply(d, h).failed
          .foreach(e => Log.Web.error(s"sending hint failed: $e"))
      val descriptions = Signals.fromFuture(
        registry.lookup(Bindings.descriptions, remote).apply()
          .map(_.map(n => n.id -> n).toMap))

      val bookmarks = Var.empty[Bindings.Bookmarks]

      val postBookmarkF = (set: Bindings.SetBookmark) => registry.lookup(Bindings.bookmarks, remote).apply(set).map { bms =>
        bookmarks.set(bms)
        bms
      }

      postBookmarkF(None)

      val app = new ReaderApp(requestContents = requestContents,
                              hint = hint,
                              descriptions = descriptions,
                              bookmarks = bookmarks,
                              postBookmarkF = postBookmarkF)

      app.triggerDispatch.fire()
    }
  }

  //	@JSExport(name = "spore")
  //	def spore(id: String, dataJson: String): Unit = {
  //
  //		offlineMode = true
  //
  //		bookmarks = Future.successful(Map())
  //		val (desc, content) = upickle.default.read[(Description, Contents)](dataJson)
  //		descriptions = Future.successful(Map(id -> desc))
  //		contents = Map(desc.id -> Future.successful(content))
  //
  //		setBody(Body(frag = div("loading data …")), scrolltop = true)
  //
  //		triggerDispatch(())
  //
  //
  //	}


}
