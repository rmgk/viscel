package visceljs.connection

import com.github.plokhotnyuk.jsoniter_scala.core.*
import org.scalajs.dom
import rescala.default.*
import viscel.shared.*
import viscel.store.{Book, BookToContents, DBParser}
import visceljs.ViscelJS.fetchbuffer
import visceljs.storage.{LocalForageInstance, Storing, localforage}
import viscel.shared.JsoniterCodecs.{*, given}
import kofre.base.Lattice
import org.scalajs.dom.{HttpMethod, Request, RequestInfo, RequestInit, fetch}
import viscel.shared.BookmarksMap.BookmarksMap

import java.io.IOException
import java.util.NoSuchElementException
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.typedarray.{AB2TA, Int8Array, TypedArrayBuffer}
import scala.util.chaining.scalaUtilChainingOps

class ContentConnectionManager() {

  import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

  def fetchAs[T: JsonValueCodec](url: RequestInfo): Signal[T] =
    println(s"fetching $url")
    Signal.fromFuture:
      dom.fetch(url).toFuture.flatMap: res =>
        if res.ok then
          res.arrayBuffer().toFuture
        else throw IOException(s"request to $url failed (${res.status}): ${res.statusText}")
      .map: ab =>
        readFromByteBuffer[T](TypedArrayBuffer.wrap(ab))
      .tap: fut =>
        fut.failed.foreach: ex =>
          Log.JS.error(s"fetch failed: $ex")

  val setBookmark = Evt[(Vid, Bookmark)]()
  val bookmarks =
    Storing.storedAs("bookmarksmap", Map.empty[Vid, Bookmark]) { initial =>
      setBookmark.fold(initial) {
        case (map, (vid, bm)) =>
          Lattice.merge(map, BookmarksMap.addΔ(vid, bm))
      }
    }

  bookmarks.observe { bms =>
    val res = fetchAs[BookmarksMap](Request(
      "bookmarksmap",
      new RequestInit {
        method = HttpMethod.POST
        body = writeToArray(bms).toTypedArray
      }
    ))
    res.observe { bmap =>
      bmap.foreach(setBookmark.fire)
    }
    ()
  }

  val remoteVersion: Signal[String] =
    fetchAs[String]("version")
      .withDefault("unknown")
      .recover(e => s"error »$e«")

  val descriptions = Storing.storedAs("descriptionsmap", Map.empty[Vid, Description]) { init =>
    fetchAs[Map[Vid, Description]]("descriptions")
      .recover { error =>
        Log.JS.error(s"failed to access descriptions: $error")
        init
      }
      .withDefault(init)
  }

  val lfi: LocalForageInstance = localforage.createInstance(js.Dynamic.literal("name" -> "contents"))

  private var priorContentSignal: Option[Signal[Any]] = None
  def content(vid: Vid): Signal[Option[Contents]] = {
    hint(vid, force = false)

    Log.JS.info(s"looking up content for $vid")

    val locallookup = {
      lfi.getItem[Int8Array](vid.str).toFuture
        .map((bytes: Int8Array) =>
          if bytes == null then throw NoSuchElementException(s"no local data for ${vid.str}")
          try readFromByteBuffer[Contents](TypedArrayBuffer.wrap(bytes))(JsoniterCodecs.ContentsRW)
          catch {
            case e: Throwable =>
              Log.JS.warn(s"error loading ${vid.str}: $e")
              throw new NoSuchElementException(s"could not load local data for ${vid.str}")
          }
        )
    }
    locallookup.failed.foreach { f =>
      Log.JS.warn(s"local lookup of $vid failed with $f")
    }

    val remoteLookupSignal: Signal[Option[Contents]] =
      fetchAs[Option[Contents]](s"contents/$vid")

    remoteLookupSignal.observe { oc => println(s"remote state changed for ${vid.str}: $oc") }

    priorContentSignal.foreach(_.disconnect())
    priorContentSignal = Some(remoteLookupSignal)

    val flatRemote = remoteLookupSignal.withDefault(None).recover(_ => None)

    val locallookupSignal = Signal.fromFuture(locallookup)
      .map(Some(_)).withDefault(None).recover(_ => None)

    val combined = Signal { flatRemote.value -> locallookupSignal.value }

    combined.observe({
      case (Some(rc), lc) if !lc.contains(rc) =>
        val bbuf = scala.scalajs.js.typedarray.byteArray2Int8Array(writeToArray(rc)(JsoniterCodecs.ContentsRW))
        lfi.setItem(vid.str, bbuf)
        ()
      case _ =>
    })

    combined.map { case (r, l) => r.orElse(l) }
  }

  def hint(vid: Vid, force: Boolean): Unit = {
    fetch(Request(s"${if force then "force" else ""}hint/$vid")).toFuture.failed
      .foreach(e => Log.JS.error(s"sending hint failed: $e"))
  }

  // below are some past experiment

  def fetchContents(vid: Vid) = {
    fetchbuffer(s"db4/${vid.str}").map { ab =>
      val start = System.currentTimeMillis()
      val bytes = new Int8Array(ab).toArray
      Log.JS.info(s"to array: ${System.currentTimeMillis() - start}")
      val (name, rows) = DBParser.parse(bytes)
      Log.JS.info(s"parse: ${System.currentTimeMillis() - start}")
      val book = Book.fromEntries(vid, name, rows)
      Log.JS.info(s"to book: ${System.currentTimeMillis() - start}")
      val contents = BookToContents.contents(book)
      Log.JS.info(s"to contents: ${System.currentTimeMillis() - start}")
      contents
    }
  }

  // eventualContents.foreach{contents =>
  // import org.scalajs.dom.experimental.serviceworkers.CacheStorage
  // import org.scalajs.dom.experimental.{RequestInfo, URL}
  // import scala.scalajs.js
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
  // }

}
