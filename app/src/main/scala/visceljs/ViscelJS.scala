package visceljs

import loci.communicator.ws.akka.WS
import loci.registry.Registry
import loci.transmitter.RemoteRef
import org.scalajs.dom
import rescala.default._
import rescala.extra.distributables.LociDist
import rescala.extra.lattices.IdUtil
import rescala.extra.lattices.IdUtil.Id
import rescala.extra.lattices.Lattice
import scalatags.JsDom.implicits.stringFrag
import scalatags.JsDom.tags.body
import viscel.shared.Bindings.SetBookmark
import viscel.shared._
import visceljs.render.{Front, Index, View}

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue


object ViscelJS {

  val wsUri: String = {
    val wsProtocol = if (dom.document.location.protocol == "https:") "wss" else "ws"
    s"$wsProtocol://${dom.document.location.host}${dom.document.location.pathname}ws"
  }

  val replicaID: Id = IdUtil.genId()

  def main(args: Array[String]): Unit = {
    dom.document.body = body("loading data …").render
    val registry = new Registry
    val connection: Future[RemoteRef] = registry.connect(WS(wsUri))
    connection.foreach { remote =>
      val requestContents = registry.lookup(Bindings.contents, remote)
      val hint: (Description, Boolean) => Unit =
        (d, h) => registry.lookup(Bindings.hint, remote).apply(d, h).failed
          .foreach(e => Log.JS.error(s"sending hint failed: $e"))

      val descriptionFuture = registry.lookup(Bindings.descriptions, remote).apply()
      val descriptions = Signals.fromFuture(descriptionFuture.map { desc => desc.map(n => n.id -> n).toMap })

      val setBookmark = Evt[(Vid, Bookmark)]
      val bookmarksCRDT = setBookmark.fold(BookmarksMap.empty){ case (map, (vid, bm)) =>
        Lattice.merge(map, map.addΔ(vid, bm)(replicaID))
      }

      LociDist.distribute(bookmarksCRDT, registry)(Bindings.bookmarksMapBindig)

      val bookmarks = bookmarksCRDT.map(_.bindings)


      val postBookmarkF: SetBookmark => Unit = { set: Bindings.SetBookmark =>
        set.foreach{ bm =>
          setBookmark.fire(bm._1.id -> bm._2)
        }
      }


      postBookmarkF(None)

      val manualStates = Evt[AppState]()

      val actions = new Actions(hint = hint, postBookmarkF = postBookmarkF, manualStates = manualStates)
      val index = new Index(actions, bookmarks, descriptions)
      val front = new Front(actions)
      val view = new View(actions)
      val app = new ReaderApp(requestContents = requestContents,
                              descriptions = descriptions,
                              bookmarks = bookmarks,
                              hint = hint
                              )

      val bodySig = app.makeBody(index, front, view, manualStates)
      val safeBodySignal = bodySig

      val bodyParent = dom.document.body.parentElement
      bodyParent.removeChild(dom.document.body)
      import rescala.extra.Tags._
      safeBodySignal.asModifier.applyTo(bodyParent)
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
