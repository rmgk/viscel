package visceljs

import org.scalajs.dom
import rescala._
import retier.communicator.ws.akka.WS
import retier.registry.Registry
import retier.transmitter.RemoteRef
import viscel.shared._
import visceljs.render.{Front, Index, View}

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scalatags.JsDom.implicits.stringFrag
import scalatags.JsDom.tags.body


object ViscelJS {

  val wsUri: String = {
    val wsProtocol = if (dom.document.location.protocol == "https:") "wss" else "ws"
    s"$wsProtocol://${dom.document.location.host}${dom.document.location.pathname}ws"
  }

  def main(args: Array[String]): Unit = {
    dom.document.body = body("loading data …").render
    val registry = new Registry
    val connection: Future[RemoteRef] = registry.request(WS(wsUri))
    connection.foreach { remote =>
      val requestContents = registry.lookup(Bindings.contents, remote)
      val hint: (Description, Boolean) => Unit =
        (d, h) => registry.lookup(Bindings.hint, remote).apply(d, h).failed
          .foreach(e => Log.Web.error(s"sending hint failed: $e"))

      val descriptionFuture = registry.lookup(Bindings.descriptions, remote).apply()
      val descriptions = Signals.fromFuture(descriptionFuture.map { desc => desc.map(n => n.id -> n).toMap })
      val bookmarks = Var.empty[Bindings.Bookmarks]

      val postBookmarkF = { (set: Bindings.SetBookmark) =>
        registry.lookup(Bindings.bookmarks, remote).apply(set).map { bms =>
          bookmarks.set(bms)
          bms
        }
      }.andThen(fut => {fut.onComplete(r => Log.Web.debug(s"retrieved bookmarks successful: ${r.isSuccess}")); fut})


      postBookmarkF(None)

      val manualStates = Evt[AppState]()

      val actions = new Actions(hint = hint, postBookmarkF = postBookmarkF, manualStates = manualStates)
      val index = new Index(actions, bookmarks, descriptions)
      val front = new Front(actions)
      val view = new View(actions)
      val app = new ReaderApp(requestContents = requestContents,
                              descriptions = descriptions,
                              bookmarks = bookmarks,
                              )

      dom.document.body = app.makeBody(index, front, view, manualStates)
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
