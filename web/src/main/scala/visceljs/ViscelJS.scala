package visceljs

import io.circe.Decoder
import io.circe.parser.decode
import org.scalajs.dom
import org.scalajs.dom.raw.HashChangeEvent
import rescala.{Evt, Observe, Signal, Signals, Var, _}
import retier.communicator.ws.akka.WS
import retier.registry.Registry
import retier.transmitter.RemoteRef
import viscel.shared._

import scala.collection.immutable.Map
import scala.collection.mutable
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scalatags.JsDom.implicits.stringFrag
import scalatags.JsDom.tags.div


object ViscelJS {

  var offlineMode = false

  def ajax[R: Decoder](path: String): Future[R] =
    if (offlineMode) Future.failed(new Throwable("offline mode"))
    else {
      val res = dom.ext.Ajax.get(url = path)
        .map { res => decode[R](res.responseText).toTry.get }
      res.failed.foreach { e =>
        Log.Web.error(s"request $path failed with ${e.getMessage}")
      }
      res
    }

  var requestContents: String => Future[Option[Contents]] = _

  implicit val readAssets: Decoder[List[SharedImage]] = Predef.implicitly[Decoder[List[SharedImage]]]

  val bookmarks: Var[Map[String, Int]] = Var.empty
  private val descriptionSource = Var.empty[Signal[Map[String, Description]]]
  var descriptions: Signal[Map[String, Description]] = descriptionSource.flatten

  private val contents: scala.collection.mutable.Map[String, Signal[Contents]] = mutable.Map()

  val triggerDispatch: Evt[Unit] = Evt[Unit]
  triggerDispatch.observe(_ => Actions.dispatchPath(dom.window.location.hash.substring(1)))
  dom.window.onhashchange = { (ev: HashChangeEvent) =>
    triggerDispatch.fire()
  }

  def content(nar: Description): Signal[Contents] = {
    contents.getOrElseUpdate(nar.id,
                             Signals.fromFuture(requestContents(nar.id).map(_.get)))
  }


  var hint: (Description, Boolean) => Unit = _

  var postBookmarkF: Bindings.SetBookmark => Future[Bindings.Bookmarks] = _

  def postBookmark(nar: Description, pos: Int): Unit = {
    postBookmarkF(Some((nar, pos))).failed.foreach(e => Log.Web.error(s"posting bookmarks failed: $e"))
  }


  private var titleObserver: Observe = null
  def setBody(abody: Body, scrolltop: Boolean): Unit = {
    dom.document.onkeydown = abody.keypress
    dom.document.body.innerHTML = ""
    if (titleObserver != null) titleObserver.remove()
    titleObserver = abody.title.observe(dom.document.title = _)
    dom.document.body.setAttribute("id", abody.id)
    dom.document.body.appendChild(abody.frag.render)
    if (scrolltop) Actions.scrollTop()
  }

  def toggleFullscreen(): Unit = {
    val doc = scalajs.js.Dynamic.global.document
    val de = doc.documentElement

    def getDefined[T](ts: T*): Option[T] = ts.find(v => v != null && !scalajs.js.isUndefined(v))

    def fullscreenElement = getDefined(doc.fullscreenElement,
                                       doc.webkitFullscreenElement,
                                       doc.mozFullScreenElement,
                                       doc.msFullscreenElement)

    def requestFullscreen = getDefined(
      de.requestFullscreen,
      de.msRequestFullscreen,
      de.mozRequestFullScreen,
      de.webkitRequestFullscreen)

    def exitFullscreen = getDefined(
      doc.exitFullscreen,
      doc.webkitExitFullscreen,
      doc.mozCancelFullScreen,
      doc.msExitFullscreen)

    fullscreenElement match {
      case None => requestFullscreen.foreach(_.call(de))
      case Some(e) => exitFullscreen.foreach(_.call(doc))
    }
  }

  def main(args: Array[String]): Unit = {
    setBody(Body(frag = div("loading data …")), scrolltop = true)
    val registry = new Registry
    val connection: Future[RemoteRef] = registry.request(WS(s"ws://${dom.window.location.host}/ws"))
    connection.foreach { remote =>
      requestContents = registry.lookup(Bindings.contents, remote)
      hint = (d, h) => registry.lookup(Bindings.hint, remote).apply(d, h).failed
        .foreach(e =>Log.Web.error(s"sending hint failed: $e"))
      descriptionSource.set(Signals.fromFuture(
        registry.lookup(Bindings.descriptions, remote).apply()
          .map(_.map(n => n.id -> n).toMap)))
      postBookmarkF = set => registry.lookup(Bindings.bookmarks, remote).apply(set).map{ bms =>
        bookmarks.set(bms)
        bms
      }

      postBookmarkF(None)

      triggerDispatch.fire()
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
