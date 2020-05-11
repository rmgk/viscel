package visceljs

import java.util.NoSuchElementException

import loci.communicator.ws.javalin.WS
import loci.registry.Registry
import loci.transmitter.RemoteRef
import org.scalajs.dom
import rescala.default._
import rescala.extra.distributables.LociDist
import rescala.extra.lattices.IdUtil.Id
import rescala.extra.lattices.{IdUtil, Lattice}
import scalatags.JsDom.implicits.stringFrag
import scalatags.JsDom.tags.body
import viscel.shared.BookmarksMap._
import viscel.shared.UpickleCodecs._
import viscel.shared._
import visceljs.render.{Front, Index, View}

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
import scala.util.{Failure, Success}


//@JSImport("localforage", JSImport.Namespace)
@JSGlobal
@js.native
object localforage extends js.Object with LocalForageInstance {
  def createInstance(config: js.Any): LocalForageInstance = js.native
}

@js.native
trait LocalForageInstance extends js.Object {
def setItem(key: String, value: js.Any): js.Promise[Unit] = js.native
def getItem[T](key: String): js.Promise[T] = js.native
}

class BookmarkManager(registry: Registry) {
  val setBookmark = Evt[(Vid, Bookmark)]
  val bookmarks   =
    Storing.storedAs("bookmarksmap", Map.empty[Vid, Bookmark]) { initial =>
      setBookmark.fold(initial) { case (map, (vid, bm)) =>
        Lattice.merge(map, BookmarksMap.addΔ(vid, bm))
      }
    }

  LociDist.distribute(bookmarks, registry)(Bindings.bookmarksMapBindig)

  def postBookmarkF(vid: Vid, bookmark: Bookmark): Unit = setBookmark.fire(vid -> bookmark)
}


class ContentConnectionManager(registry: Registry) {
  import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

  val descriptions = Storing.storedAs("descriptionsmap", Map.empty[Vid, Description])(Var(_)).asInstanceOf[Var[Map[Vid, Description]]]

  val wsUri: String = {
    val wsProtocol = if (dom.document.location.protocol == "https:") "wss" else "ws"
    s"$wsProtocol://${dom.document.location.host}${dom.document.location.pathname}ws"
  }

  def connect(): Unit = {
    Log.JS.info(s"trying to connect to $wsUri")
    val connection: Future[RemoteRef] = registry.connect(WS(wsUri))
    connection.flatMap(updateDescriptions)
    connection.failed.foreach(_ => connect())
  }
  registry.remoteLeft.foreach(_ => connect())
  connect()



  val lfi: LocalForageInstance = localforage.createInstance(js.Dynamic.literal("name" -> "contents"))

  def content(vid: Vid): Signal[Contents] = {
    hint(vid, force = false)

    Log.JS.info(s"looking up content for $vid")

    val emptyContents = Contents(Gallery.empty, Nil)
    val locallookup   =
      lfi.getItem[String](vid.str).toFuture
         .map((str: String) =>
                try upickle.default.read[Contents](str)
                catch {
                  case _: Throwable => throw new NoSuchElementException(s"could not load local data for ${vid.str}")
                })
    locallookup.failed.foreach { f =>
      Log.JS.warn(s"local lookup of $vid failed with $f")
    }
    val remoteLookup: Future[Contents] = {
      registry.remotes.find(_.connected).map { remote =>
        val requestContents  = registry.lookup(Bindings.contents, remote)
        requestContents(vid).map(_.getOrElse(emptyContents))
        //eventualContents.onComplete(t => Log.JS.debug(s"received contents for ${vid} (sucessful: ${t.isSuccess})"))
      }.getOrElse(Future.failed(new IllegalStateException("not connected")))
    }

    val result = Var(emptyContents)
    var remoteMerged = false
    locallookup.filter(_ => !remoteMerged).foreach{lc =>
      Log.JS.info(s"found local content for $vid")
      result.set(lc)
    }
    remoteLookup.foreach {rc =>
      remoteMerged = true
      Log.JS.info(s"found remote content for $vid")
      result.set(rc)
      lfi.setItem(vid.str, upickle.default.write(rc))
    }

    result
  }

  def hint(vid: Vid, force: Boolean): Unit = {
    registry.remotes.filter(_.connected).foreach { connection =>
      registry.lookup(Bindings.hint, connection).apply(vid, force).failed
              .foreach(e => Log.JS.error(s"sending hint failed: $e"))
    }
  }


  //eventualContents.foreach{contents =>
  //import org.scalajs.dom.experimental.serviceworkers.CacheStorage
  //import org.scalajs.dom.experimental.{RequestInfo, URL}
  //import scala.scalajs.js
  //  Log.JS.info(s"prefetching ${vid} ")
  //  def toUrl(blob: Blob) = new URL(Definitions.path_blob(blob), dom.document.location.href)
  //  val urls = contents.gallery.toSeq.iterator.map(si => toUrl(si.blob))
  //  val caches = dom.window.asInstanceOf[js.Dynamic].caches.asInstanceOf[CacheStorage]
  //  caches.open(s"vid${vid}").`then`[Unit]{ cache =>
  //    cache.addAll(js.Array(urls.map[RequestInfo](_.href).toSeq : _*))
  //  }
  //  //contents.gallery.toSeq.foreach{image =>
  //  //  Log.JS.info(image.blob.toString)
  //  //  image.blob.foreach { blob =>
  //  //    val url = new URL(Definitions.path_blob(blob), dom.document.location.href)
  //  //    Log.JS.info(url.href)
  //  //    val navigator  = dom.window.navigator
  //  //    Log.JS.info(s"navigator : $navigator")
  //  //    val controller = navigator.asInstanceOf[js.Dynamic].serviceWorker.controller
  //  //    Log.JS.info(s"controller : $controller")
  //  //    controller.postMessage(
  //  //      js.Dynamic.literal(
  //  //        "command" -> "add",
  //  //        "vid" -> nar.id.str,
  //  //        "url" -> url.href
  //  //        )
  //  //      )
  //  //  }
  //  //}
  //}




  private def updateDescriptions(remote: RemoteRef): Future[_] = {
    val lookup = registry.lookup(Bindings.descriptions, remote).apply()
    lookup.onComplete {
      case Success(descriptionMap)     =>
        descriptions.set(descriptionMap)
      case Failure(exception) =>
        Log.JS.error("failed to fetch descriptions")
    }
    lookup
  }
}


object ViscelJS {

  val replicaID: Id = IdUtil.genId()

  def main(args: Array[String]): Unit = {
    dom.document.body = body("loading data …").render
    val registry = new Registry

    val bookmarkManager = new BookmarkManager(registry)
    val ccm = new ContentConnectionManager(registry)

    val actionsEv = Events.fromCallback[AppState] { cb =>
      new Actions(hint = ccm.hint, postBookmarkF = bookmarkManager.postBookmarkF, manualStates = cb)
    }

    val actions = actionsEv.value
    val index = new Index(actions, bookmarkManager.bookmarks, ccm.descriptions)
    val front = new Front(actions)
    val view = new View(actions)
    val app = new ReaderApp(content = ccm.content,
                            descriptions = ccm.descriptions,
                            bookmarks = bookmarkManager.bookmarks
                            )

    val bodySig = app.makeBody(index, front, view, actionsEv.event)
    val safeBodySignal = bodySig

    val bodyParent = dom.document.body.parentElement
    bodyParent.removeChild(dom.document.body)
    import rescala.extra.Tags._
    safeBodySignal.asModifier.applyTo(bodyParent)

  }

}
