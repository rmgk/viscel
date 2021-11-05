package visceljs

import loci.registry.Registry
import org.scalajs.dom
import org.scalajs.dom.experimental.{Fetch, HttpMethod, RequestInit}
import rescala.default._
import rescala.extra.Tags._
import rescala.extra.lattices.IdUtil
import rescala.extra.lattices.IdUtil.Id
import scalatags.JsDom.implicits.stringFrag
import scalatags.JsDom.tags.{body, h1, p}
import visceljs.connection.{BookmarkManager, ContentConnectionManager, ServiceWorker}
import visceljs.render.{DetailsPage, ImagePage, OverviewPage, Snippets}

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.typedarray.ArrayBuffer

case class MetaInfo(
    version: String,
    remoteVersion: Signal[String],
    serviceState: Signal[String],
    connection: Signal[Int],
    reconnecting: Signal[Int]
)

object ViscelJS {

  val baseurl = ""

  def fetchbuffer(
      endpoint: String,
      method: HttpMethod = HttpMethod.GET,
      body: Option[String] = None
  ): Future[ArrayBuffer] = {

    val ri = js.Dynamic.literal(method = method).asInstanceOf[RequestInit]

    body.foreach { content =>
      ri.body = content
      ri.headers = js.Dictionary("Content-Type" -> "application/json;charset=utf-8")
    }

    //authentication.foreach{ user =>
    //  if (js.isUndefined(ri.headers)) ri.headers = js.Dictionary.empty[String]
    //  ri.headers.asInstanceOf[js.Dictionary[String]]("Authorization") = s"Token ${user.token}"
    //}

    Fetch.fetch(baseurl + endpoint, ri).toFuture
      .flatMap(_.arrayBuffer().toFuture)
  }

  val replicaID: Id = IdUtil.genId()

  def main(args: Array[String]): Unit = {
    dom.document.body = body("loading data …").render

    val swstate = ServiceWorker.register()

    val registry = new Registry

    val bookmarkManager = new BookmarkManager(registry)
    val ccm             = new ContentConnectionManager(registry)
    ccm.autoreconnect()

    val actions = new Actions(ccm, bookmarkManager)

    //ccm.remoteVersion.observe{v =>
    //  if (!(v == "unknown" || v.startsWith("error")) && v != viscel.shared.Version.str) {
    //    ServiceWorker.unregister().andThen(_ => dom.window.location.reload(true))
    //  }
    //}

    val meta = MetaInfo(viscel.shared.Version.str, ccm.remoteVersion, swstate, ccm.connectionStatus, ccm.reconnecting)

    val index = new OverviewPage(meta, actions, bookmarkManager.bookmarks, ccm.descriptions)
    val front = new DetailsPage(actions)
    val view  = new ImagePage(actions)
    val app =
      new ReaderApp(content = ccm.content, descriptions = ccm.descriptions, bookmarks = bookmarkManager.bookmarks)

    val bodySig = app.makeBody(index, front, view)

    val metaLoading = Snippets.meta(meta)

    def loading =
      body(
        h1("This is basically a loading screen"),
        p(
          "However, this does not necessarily refresh by itself, try reloading at some point. If that does not help, there may just be nothing here."
        ),
        metaLoading.asModifier
      )

    val bodyParent = dom.document.body.parentElement
    bodyParent.removeChild(dom.document.body)
    import rescala.extra.Tags._
    bodySig.map {
      case Some(body) => body
      case None       => loading
    }.recover { error =>
      error.printStackTrace(System.err)
      body(h1("An error occurred"), p(error.toString), metaLoading.asModifier)
    }.asModifier.applyTo(bodyParent)

  }
}
