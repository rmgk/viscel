package visceljs

import loci.registry.Registry
import loci.transmitter.RemoteRef
import org.scalajs.dom
import rescala.default._
import rescala.extra.lattices.IdUtil
import rescala.extra.lattices.IdUtil.Id
import scalatags.JsDom.implicits.stringFrag
import scalatags.JsDom.tags.body
import visceljs.connection.{BookmarkManager, ContentConnectionManager, ServiceWorker}
import visceljs.render.{Front, Index, View}

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scala.util.Try


case class MetaInfo(version: String, serviceState: Signal[String], connection: Signal[Option[Try[RemoteRef]]])

@JSExportTopLevel("ViscelJS")
object ViscelJS {

  val replicaID: Id = IdUtil.genId()

  @JSExport
  def run(version: String): Unit = {
    dom.document.body = body("loading data …").render

    val swstate = ServiceWorker.register()



    val registry = new Registry

    val bookmarkManager = new BookmarkManager(registry)
    val ccm             = new ContentConnectionManager(registry)
    ccm.autoreconnect()

    val actionsEv = Events.fromCallback[AppState] { cb =>
      new Actions(hint = ccm.hint, postBookmarkF = bookmarkManager.postBookmarkF, manualStates = cb)
    }

    val meta = MetaInfo(version, swstate, ccm.connectionStatus)


    val actions = actionsEv.value
    val index   = new Index(meta, actions, bookmarkManager.bookmarks, ccm.descriptions)
    val front   = new Front(actions)
    val view    = new View(actions)
    val app     = new ReaderApp(content = ccm.content,
                                descriptions = ccm.descriptions,
                                bookmarks = bookmarkManager.bookmarks
                                )

    val bodySig        = app.makeBody(index, front, view, actionsEv.event)
    val safeBodySignal = bodySig

    val bodyParent = dom.document.body.parentElement
    bodyParent.removeChild(dom.document.body)
    import rescala.extra.Tags._
    safeBodySignal.asModifier.applyTo(bodyParent)

  }

}
