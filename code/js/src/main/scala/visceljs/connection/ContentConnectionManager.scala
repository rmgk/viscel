package visceljs.connection

import java.util.NoSuchElementException

import com.github.plokhotnyuk.jsoniter_scala.core._
import loci.communicator.ws.javalin.WS
import loci.registry.Registry
import loci.transmitter.RemoteRef
import org.scalajs.dom
import rescala.default._
import rescala.reactives.RExceptions.EmptySignalControlThrowable
import viscel.shared._
import viscel.store.{Book, BookToContents, DBParser}
import visceljs.ViscelJS.fetchbuffer
import visceljs.storage.{LocalForageInstance, Storing, localforage}

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.typedarray.{Int8Array, TypedArrayBuffer}

class ContentConnectionManager(registry: Registry) {

  import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

  val wsUri: String = {
    val wsProtocol = if (dom.document.location.protocol == "https:") "wss" else "ws"
    s"$wsProtocol://${dom.document.location.host}${dom.document.location.pathname}ws"
  }

  val joined = Events.fromCallback[RemoteRef] { cb =>
    registry.remoteJoined.foreach(cb)
  }.event
  val left = Events.fromCallback[RemoteRef] { cb =>
    registry.remoteLeft.foreach(cb)
  }.event

  val connectionStatusChanged = joined || left

  val connectedRemotes = connectionStatusChanged.fold[List[RemoteRef]](Nil) { (_, _) =>
    registry.remotes.filter(_.connected)
  }

  val connectionStatus: Signal[Int] = connectedRemotes.map(_.size)

  val mainRemote = connectedRemotes.map(_.headOption.getOrElse(throw EmptySignalControlThrowable))

  val remoteVersion: Signal[String] =
    joined.map(rr => Signals.fromFuture(registry.lookup(Bindings.version, rr)))
      .latest(Signal { "unknown" }).flatten
      .recover(e => s"error »$e«")

  val descriptions = Storing.storedAs("descriptionsmap", Map.empty[Vid, Description]) { init =>
    mainRemote.map { rr =>
      Signals.fromFuture(registry.lookup(Bindings.descriptions, rr).apply())
    }.flatten.changed
      .recover { error =>
        Log.JS.error(s"failed to access descriptions: $error")
        None
      }.latest(init)
  }(JsoniterCodecs.MapVidDescriptionCodec)

  val connectionAttempt: Evt[Unit] = Evt[Unit]()

  val reconnecting: Signal[Int] = Events.foldAll(0)(acc =>
    Seq(
      connectionAttempt >> { _ => acc + 1 },
      joined >> { _ => 0 }
    )
  )

  def connect(): Future[RemoteRef] = {
    Log.JS.info(s"trying to connect to $wsUri")
    connectionAttempt.fire()
    registry.connect(WS(wsUri))
  }

  def connectLoop(): Unit = {
    connect().failed.foreach { err =>
      Log.JS.error(s"connection failed »$err«")
      dom.window.setTimeout(() => connectLoop(), 10000)
    }
  }

  def autoreconnect(): Unit = {
    left.filter(_ => connectionStatus.value == 0).observe(_ => connectLoop())
    connectLoop()
  }

  val lfi: LocalForageInstance = localforage.createInstance(js.Dynamic.literal("name" -> "contents"))

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

  private var priorContentSignal: Option[Signal[Any]] = None
  def content(vid: Vid): Signal[Option[Contents]] = {
    hint(vid, force = false)

    Log.JS.info(s"looking up content for $vid")

    val locallookup = {
      lfi.getItem[Int8Array](vid.str).toFuture
        .map((bytes: Int8Array) =>
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

    val remoteLookupSignal: Signal[Signal[Option[Contents]]] = mainRemote.map { remote =>
      //Signals.fromFuture(fetchContents(vid))
      Signals.fromFuture(registry.lookup(Bindings.contents, remote).apply(vid))
    }

    priorContentSignal.foreach(_.disconnect())
    priorContentSignal = Some(remoteLookupSignal)

    val flatRemote = remoteLookupSignal.flatten.withDefault(None).recover(_ => None)

    val locallookupSignal = Signals.fromFuture(locallookup)
      .map(Some(_)).withDefault(None).recover(_ => None)

    val combined = Signal { flatRemote.value -> locallookupSignal.value }

    combined.observe({
      case (Some(rc), lc) if !lc.contains(rc) =>
        val bbuf = scala.scalajs.js.typedarray.byteArray2Int8Array(writeToArray(rc)(JsoniterCodecs.ContentsRW))
        lfi.setItem(vid.str, bbuf)
      case _ =>
    })

    combined.map { case (r, l) => r.orElse(l) }
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

}
