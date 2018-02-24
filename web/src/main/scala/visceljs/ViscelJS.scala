package visceljs

import io.circe.Decoder
import io.circe.generic.auto._
import io.circe.parser.decode
import org.scalajs.dom
import org.scalajs.dom.raw.HashChangeEvent
import rescala.{Observe, Evt, Signal, Signals, Var}
import viscel.shared._

import scala.collection.immutable.Map
import scala.collection.mutable
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.URIUtils.encodeURIComponent
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
        Console.println(s"request $path failed with ${e.getMessage}")
      }
      res
    }

  implicit val readAssets: Decoder[List[SharedImage]] = Predef.implicitly[Decoder[List[SharedImage]]]

  val bookmarkSource: Var[Signal[Map[String, Int]]] = Var(Signals.fromFuture(ajax[Map[String, Int]]("bookmarks")))
  val bookmarks: Signal[Map[String, Int]] = bookmarkSource.flatten
  val descriptions: Signal[Map[String, Description]] = Signals.fromFuture(ajax[List[Description]]("narrations").map(_.map(n => n.id -> n).toMap))

  private val contents: scala.collection.mutable.Map[String, Signal[Contents]] = mutable.Map()

  val triggerDispatch: Evt[Unit] = Evt[Unit]
  triggerDispatch.observe(_ => Actions.dispatchPath(dom.window.location.hash.substring(1)))
  dom.window.onhashchange = { (ev: HashChangeEvent) =>
    triggerDispatch.fire()
  }

  def content(nar: Description): Signal[Contents] = {
    contents.getOrElseUpdate(nar.id,
                             Signals.fromFuture(ajax[Contents](s"narration/${encodeURIComponent(nar.id)}")))
  }


  def hint(nar: Description, force: Boolean = false): Unit = dom.ext.Ajax.post(s"hint/narrator/${encodeURIComponent(nar.id)}" + (if (force) "?force=true" else ""))

  def postBookmark(nar: Description, pos: Int): Future[Map[String, Int]] = {
    val res = dom.ext.Ajax.post("bookmarks", s"narration=${encodeURIComponent(nar.id)}&bookmark=$pos", headers = Map("Content-Type" -> "application/x-www-form-urlencoded; charset=UTF-8"))
      .map(res => decode[Map[String, Int]](res.responseText).toTry.get)
    bookmarkSource.set(Signals.fromFuture(res))
    res
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
    triggerDispatch.fire()
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
