package visceljs

import io.circe.Decoder
import org.scalajs.dom
import org.scalajs.dom.experimental.URL
import org.scalajs.dom.html
import org.scalajs.dom.raw.HashChangeEvent
import rescala._
import viscel.shared.{Bindings, Contents, Description, Log, SharedImage}
import visceljs.render.{Front, Index, View}
import visceltags._

import scala.collection.immutable.Map
import scala.collection.mutable
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scalatags.JsDom
import scalatags.JsDom.all.{body, id, stringAttr, stringFrag}



class ReaderApp(requestContents: String => Future[Option[Contents]],
                postBookmarkF: Bindings.SetBookmark => Future[Bindings.Bookmarks],
                val hint: (Description, Boolean) => Unit,
                val descriptions: Signal[Map[String, Description]],
                val bookmarks: Signal[Map[String, Int]],
               ) {


  lazy val actions = new Actions(this)
  lazy val index = new Index(actions, bookmarks, descriptions)
  lazy val front = new Front(actions)
  lazy val view = new View(actions)

  def fromCallback[T](cb: (T => Unit) => Unit): Event[T] = {
    val evt = Evt[T]
    cb(evt.fire)
    evt
  }

  val bodyEvt: Evt[JsDom.TypedTag[html.Body]] = Evt()
  val bodySignal = bodyEvt.latest()
  lazy val bodyElement: html.Body = bodySignal
    .withDefault(body("loading more data"))
    .recover{case t => body(t.toString)}
    .asFrag.render



  implicit val readAssets: Decoder[List[SharedImage]] = Predef.implicitly[Decoder[List[SharedImage]]]

  private val contents: scala.collection.mutable.Map[String, Signal[Contents]] = mutable.Map()

  val hashChange: Event[HashChangeEvent] = fromCallback[HashChangeEvent](dom.window.onhashchange = _)
  val triggerDispatch: Evt[Unit] = Evt[Unit]
  triggerDispatch.observe(_ => actions.dispatchPath(dom.window.location.hash.substring(1)))
  dom.window.onhashchange = { (ev: HashChangeEvent) =>
    new URL(ev.oldURL).hash
    Log.Web.debug(s"$ev: ${ev.oldURL}, ${ev.newURL}")
    triggerDispatch.fire()
  }

  def content(nar: Description): Signal[Contents] = {
    contents.getOrElseUpdate(nar.id,
                             Signals.fromFuture(requestContents(nar.id).map(_.get)))
  }


  def postBookmark(nar: Description, pos: Int): Unit = {
    postBookmarkF(Some((nar, pos))).failed.foreach(e => Log.Web.error(s"posting bookmarks failed: $e"))
  }


  private var titleObserver: Observe = null
  def setBody(abody: Body, scrolltop: Boolean): Unit = {
    dom.document.onkeydown = abody.keypress
    if (titleObserver != null) titleObserver.remove()
    titleObserver = abody.title.observe(dom.document.title = _)
    bodyEvt.fire(body(abody.frag, id := abody.id))
    if (scrolltop) actions.scrollTop()
  }


}
