package visceljs

import loci.registry.Registry
import org.scalajs.dom
import rescala.default._
import rescala.extra.lattices.IdUtil
import rescala.extra.lattices.IdUtil.Id
import scalatags.JsDom.implicits.stringFrag
import scalatags.JsDom.tags.body
import visceljs.connection.{BookmarkManager, ContentConnectionManager, ServiceWorker}
import visceljs.render.{Front, Index, View}

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

case class MetaInfo(version: String, remoteVersion: Signal[String], serviceState: Signal[String], connection: Signal[Int], reconnecting: Signal[Int])

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

    val actions = new Actions(ccm, bookmarkManager)

    val meta = MetaInfo(version, ccm.remoteVersion, swstate, ccm.connectionStatus, ccm.reconnecting)


    val index   = new Index(meta, actions, bookmarkManager.bookmarks, ccm.descriptions)
    val front   = new Front(actions)
    val view    = new View(actions)
    val app     = new ReaderApp(content = ccm.content,
                                descriptions = ccm.descriptions,
                                bookmarks = bookmarkManager.bookmarks
                                )

    val bodySig        = app.makeBody(index, front, view)
    val safeBodySignal = bodySig

    val bodyParent = dom.document.body.parentElement
    bodyParent.removeChild(dom.document.body)
    import rescala.extra.Tags._
    safeBodySignal.asModifier.applyTo(bodyParent)

  }
}
