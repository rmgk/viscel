package visceljs

import io.circe.Decoder
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.raw.HashChangeEvent
import rescala._
import rescala.reactives.RExceptions.EmptySignalControlThrowable
import viscel.shared.{Bindings, Contents, Description, Log, SharedImage}
import visceljs.Definitions.{path_asset, path_front, path_main}
import visceljs.render.{Front, Index, View}
import visceljs.visceltags._

import scala.collection.immutable.Map
import scala.collection.mutable
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.URIUtils.decodeURIComponent
import scalatags.JsDom.TypedTag
import scalatags.JsDom.all.{body, stringFrag}


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


  implicit val readAssets: Decoder[List[SharedImage]] = Predef.implicitly[Decoder[List[SharedImage]]]

  private val contents: scala.collection.mutable.Map[String, Signal[Contents]] = mutable.Map()

  sealed trait AppState
  case object IndexState extends AppState
  case class FrontState(desc: Signal[Data]) extends AppState
  case class ViewState(data: Signal[Data]) extends AppState

  def getDataSignal(id: String): Signal[Data] = {
    Signal.dynamic {
      val description = descriptions.value(id)
      val bm = bookmarks().getOrElse(description.id, 0)
      val cont = content(description): @unchecked
      Data(description, cont.value, bm)
    }
  }

  def pathToState(path: String): AppState = {
    val paths = List(path.substring(1).split("/"): _*)
    Log.Web.debug(s"get state for $paths")
    paths match {
      case Nil | "" :: Nil => IndexState
      case encodedId :: Nil =>
        val id = decodeURIComponent(encodedId)
        FrontState(getDataSignal(id))
      case encodedId :: posS :: Nil =>
        val id = decodeURIComponent(encodedId)
        val pos = Integer.parseInt(posS)
        ViewState(getDataSignal(id).map(_.move(_.first.next(pos - 1))))
      case _ => IndexState
    }
  }

  private def getHash = {
    dom.window.location.hash
  }

  val hashChange: Event[HashChangeEvent] = fromCallback[HashChangeEvent](dom.window.onhashchange = _)
  hashChange.observe(hc => Log.Web.debug(s"hash change event: $hc"))

  val hashBasedStates = hashChange.map(_ => pathToState(getHash))


  val manualStates: Evt[AppState] = Evt()

  val appStates: Event[AppState] = hashBasedStates || manualStates

  val currentAppState = appStates.latest(pathToState(getHash))

  Signal.dynamic {
    currentAppState.value match {
      case IndexState => ("main", path_main)
      case FrontState(data) => ("front", path_front(data.value.description))
      case ViewState(data) => ("view", path_asset(data.value))
    }
  }.observe { case (n, u) =>
    val h = getHash
    // for some reason, the leading # is not returned by getHash, when nothing follows
    if (h != u && !(u == "#" && h == "")) {
      Log.Web.debug(s"pushing ${(n, u)} (hash was '$h')")
      dom.window.history.pushState(null, n, u)
    }
  }

  appStates.observe(s => Log.Web.debug(s"state: $s"))

  lazy val indexBody = index.gen()

  val navigateFrontEvt: Evt[Description] = Evt()
  val navigateFront: Event[Description] = navigateFrontEvt

  val frontData: Signal[Data] = currentAppState.map {
    case FrontState(nar) => nar
    case _ => throw EmptySignalControlThrowable
  }.flatten

  lazy val bodyFront = front.gen(frontData)


  val bodyElement: html.Body = {
    // we hide everything in here, because printing the html.Body actually causes it to no longer display the inner
    // rendered signals
    val selectedBodySignal: Signal[Body] = currentAppState.map {
      case IndexState => indexBody
      case FrontState(_) => bodyFront
      case ViewState(_) => actions.viewBody
    }
    selectedBodySignal.observe(s => Log.Web.debug(s"selected body: $s"))
    val bodySignal: Signal[TypedTag[html.Body]] = selectedBodySignal.map(_.bodyTag).flatten
    val bodyTag: Signal[TypedTag[html.Body]] = bodySignal
      .withDefault(body("loading more data"))
      .recover { case t => body(t.toString) }
    bodyTag.asFrag.render
  }


  def content(nar: Description): Signal[Contents] = {
    contents.getOrElseUpdate(nar.id, {
      val eventualContents = requestContents(nar.id).map(_.get)
      eventualContents.onComplete(t => Log.Web.debug(s"received contents for ${nar.id} (sucessful: ${t.isSuccess})"))
      Signals.fromFuture(eventualContents)
    })
  }


  def postBookmark(nar: Description, pos: Int): Unit = {
    postBookmarkF(Some((nar, pos))).failed.foreach(e => Log.Web.error(s"posting bookmarks failed: $e"))
  }


  private var titleObserver: Observe = null
  def setBody(abody: Body, scrolltop: Boolean): Unit = {
    dom.document.onkeydown = abody.keypress
    if (titleObserver != null) titleObserver.remove()
    titleObserver = abody.title.observe(dom.document.title = _)
    if (scrolltop) actions.scrollTop()
  }


}
