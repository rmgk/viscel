package visceljs

import io.circe.Decoder
import org.scalajs.dom
import org.scalajs.dom.raw.HashChangeEvent
import rescala.{Evt, Observe, Signal, Signals}
import viscel.shared.{Bindings, Contents, Description, Log, SharedImage}
import visceljs.render.{Front, Index, View}

import scala.collection.immutable.Map
import scala.collection.mutable
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue



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


  implicit val readAssets: Decoder[List[SharedImage]] = Predef.implicitly[Decoder[List[SharedImage]]]

  private val contents: scala.collection.mutable.Map[String, Signal[Contents]] = mutable.Map()

  val triggerDispatch: Evt[Unit] = Evt[Unit]
  triggerDispatch.observe(_ => actions.dispatchPath(dom.window.location.hash.substring(1)))
  dom.window.onhashchange = { (ev: HashChangeEvent) =>
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
    dom.document.body.innerHTML = ""
    if (titleObserver != null) titleObserver.remove()
    titleObserver = abody.title.observe(dom.document.title = _)
    dom.document.body.setAttribute("id", abody.id)
    dom.document.body.appendChild(abody.frag.render)
    if (scrolltop) actions.scrollTop()
  }


}
