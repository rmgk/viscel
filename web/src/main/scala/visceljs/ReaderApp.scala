package visceljs

import io.circe.Decoder
import org.scalajs.dom
import org.scalajs.dom.experimental.URL
import org.scalajs.dom.html
import org.scalajs.dom.raw.HashChangeEvent
import rescala._
import rescala.reactives.RExceptions.EmptySignalControlThrowable
import viscel.shared.{Bindings, Contents, Description, Log, SharedImage}
import visceljs.Definitions.{path_asset, path_front, path_main}
import visceljs.render.View.{Goto, Mode, Next, Prev, navigationEvents}
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


  implicit val readAssets: Decoder[List[SharedImage]] = Predef.implicitly[Decoder[List[SharedImage]]]

  private val contents: scala.collection.mutable.Map[String, Signal[Contents]] = mutable.Map()

  sealed trait AppState
  case object IndexState extends AppState
  case class FrontState(id: String) extends AppState
  case class ViewState(id: String, pos: Int) extends AppState
  
  val manualStates: Evt[AppState] = Evt()


  def makeBody: html.Body = {


    lazy val actions = new Actions(this)
    lazy val index = new Index(actions, bookmarks, descriptions)
    lazy val front = new Front(actions)
    lazy val view = new View(actions)


    def pathToState(path: String): AppState = {
      val paths = List(path.substring(1).split("/"): _*)
      Log.Web.debug(s"get state for $paths")
      paths match {
        case Nil | "" :: Nil => IndexState
        case encodedId :: Nil =>
          val id = decodeURIComponent(encodedId)
          FrontState(id)
        case encodedId :: posS :: Nil =>
          val id = decodeURIComponent(encodedId)
          val pos = Integer.parseInt(posS)
          ViewState(id, pos - 1)
        case _ => IndexState
      }
    }

    def getHash: String = {
      dom.window.location.hash
    }

    val hashChange: Event[HashChangeEvent] = visceltags.eventFromCallback[HashChangeEvent](dom.window.onhashchange = _)
    hashChange.observe(hc => Log.Web.debug(s"hash change event: ${hc.oldURL} -> ${hc.newURL}"))

    val hashBasedStates = hashChange.map(hc => pathToState(new URL(hc.newURL).hash): @unchecked)



    val targetStates: Event[AppState] = hashBasedStates || manualStates

    navigationEvents.observe(n => Log.Web.debug(s"navigating $n"))

    val currentPosition: Signal[Int] = Events.foldAll(0) { (pos) =>
      Events.Match(
        navigationEvents >> {
          case Prev if pos > 0 => pos - 1
          case Next => pos + 1
          case Prev | Next => pos
          case Mode(n) => pos
          case Goto(target) => target.pos
        },
        targetStates >> {
          case ViewState(_, p) => p
          case _ => pos
        }
      )
    }

    val currentAppState: Signal[AppState] = targetStates.latest(pathToState(getHash))


    targetStates.observe(s => Log.Web.debug(s"state: $s"))


    val currentData: Signal[Data] = {
      val c = currentAppState.map {
        case FrontState(nar) => getDataSignal(nar)
        case ViewState(id, _) => getDataSignal(id)
        case _ => throw EmptySignalControlThrowable
      }.flatten
      Signal {
        c.value.atPos(currentPosition.value)
      }
    }

    {
      Signal.dynamic {
        currentAppState.value match {
          case IndexState => ("main", path_main)
          case FrontState(_) => ("front", path_front(currentData.value.description))
          case ViewState(_, _) => ("view", path_asset(currentData.value))
        }
      }.observe { case (n, u) =>
        val h = getHash
        // for some reason, the leading # is not returned by getHash, when nothing follows
        if (h != u && !(u == "#" && h == "")) {
          dom.window.history.pushState(null, n, u)
          Log.Web.debug(s"pushing ${(n, u)} (hash was '$h') history has length ${dom.window.history.length}")
        }
      }
    }

    lazy val indexBody = index.gen()
    lazy val frontBody = front.gen(currentData)
    lazy val viewBody = view.gen(currentData, View.navigate)


    val bodyElement: html.Body = {
      // printing the html.Body actually causes it to no longer display the inner rendered signals
      val bodySignal: Signal[TypedTag[html.Body]] = currentAppState.map {
        case IndexState => indexBody
        case FrontState(_) => frontBody
        case ViewState(_, _) => viewBody
      }.flatten
      val bodyTag: Signal[TypedTag[html.Body]] = bodySignal
        .withDefault(body("loading more data"))
        .recover { case t => body(t.toString) }
      bodyTag.asFrag.render
    }

    bodyElement
  }

  def getDataSignal(id: String): Signal[Data] = {
    Signal.dynamic {
      val description = descriptions.value(id)
      val bm = bookmarks().getOrElse(description.id, 0)
      val cont = content(description): @unchecked
      Data(description, cont.value, bm)
    }
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
}
