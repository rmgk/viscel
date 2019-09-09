package visceljs

import io.circe.Decoder
import org.scalajs.dom
import org.scalajs.dom.experimental.URL
import org.scalajs.dom.html
import org.scalajs.dom.raw.HashChangeEvent
import rescala.default._
import rescala.reactives.RExceptions.EmptySignalControlThrowable
import scalatags.JsDom.TypedTag
import viscel.shared.{Bookmark, Contents, Description, Log, SharedImage, Vid}
import visceljs.AppState.{FrontState, IndexState, ViewState}
import visceljs.Definitions.{path_asset, path_front, path_main}
import visceljs.Navigation.{Mode, Next, Prev, navigationEvents}
import visceljs.render.{Front, Index, View}

import scala.collection.immutable.Map
import scala.scalajs.js.URIUtils.decodeURIComponent
import scala.util.Try


class ReaderApp(content: Description => Signal[Contents],
                val descriptions: Signal[Map[Vid, Description]],
                val bookmarks: Signal[Map[Vid, Bookmark]]
               ) {


  implicit val readAssets: Decoder[List[SharedImage]] = Predef.implicitly[Decoder[List[SharedImage]]]





  def makeBody(index: Index, front: Front, view: View, manualStates: Event[AppState]):  Signal[TypedTag[html.Body]] = {


    def pathToState(path: String): AppState = {
      val paths = List(path.substring(1).split("/"): _*)
      Log.JS.debug(s"get state for $paths")
      paths match {
        case Nil | "" :: Nil => IndexState
        case encodedId :: Nil =>
          val id = Vid.from(decodeURIComponent(encodedId))
          FrontState(id)
        case encodedId :: posS :: Nil =>
          val id = Vid.from(decodeURIComponent(encodedId))
          val pos = Integer.parseInt(posS)
          ViewState(id, pos - 1)
        case _ => IndexState
      }
    }

    def getHash: String = {
      dom.window.location.hash
    }

    val hashChange: Event[HashChangeEvent] =
      Events.fromCallback[HashChangeEvent](dom.window.onhashchange = _).event
    hashChange.observe(hc => Log.JS.debug(s"hash change event: ${hc.oldURL} -> ${hc.newURL}"))

    val hashBasedStates = hashChange.map(hc => pathToState(new URL(hc.newURL).hash): @unchecked)


    val targetStates: Event[AppState] = hashBasedStates || manualStates

    val initialState = pathToState(getHash)
    val initialPos = initialState match {
      case IndexState => 1
      case FrontState(id) => 1
      case ViewState(id, pos) => pos
    }

    Log.JS.debug(s"initial state: $initialState")

    navigationEvents.observe(n => Log.JS.debug(s"navigating $n"))



    val currentAppState: Signal[AppState] = targetStates.latest(initialState)

    val unnormalizedData: Signal[Data] = currentAppState.map {
      case FrontState(nar) => getDataSignal(nar)
      case ViewState(id, _) => getDataSignal(id)
      case _ => throw EmptySignalControlThrowable
    }.flatten

    val currentPosition: Signal[Int] = Events.foldAll(initialPos) { pos =>
      Events.Match(
        navigationEvents >> {
          case Prev => math.max(pos - 1, 0)
          case Next =>
            val last = Try(unnormalizedData.readValueOnce.gallery.size - 1).getOrElse(Int.MaxValue)
            math.min(pos + 1, last)
          case Mode(_) => pos
        },
        targetStates >> {
          case ViewState(_, p) => p
          case _ => pos
        }
      )
    }
    currentPosition.observe(p => Log.JS.debug(s"current position is $p"))

    val fitType = navigationEvents.fold(0){
      case (_, Mode(i)) => i % 8
      case (i, _) => i
    }

    targetStates.observe(s => Log.JS.debug(s"state: $s"))


    val currentData: Signal[Data] = Signal {
      unnormalizedData.value.atPos(currentPosition.value)
    }

    navigationEvents.map(e => e -> currentData()).observe { case (ev, data) =>
      if (ev == Prev || ev == Next) {
        dom.window.scrollTo(0, 0)
      }
      /*val pregen =*/ data.gallery.next(1).get.map(asst => Make.asset(asst, data).render)

    }

    Signal.dynamic {
      currentAppState.value match {
        case IndexState => (path_main, "Viscel")
        case FrontState(_) =>
          val desc = currentData.value.description
          (path_front(desc), desc.name)
        case ViewState(_, _) =>
          val data = currentData.value
          (path_asset(data), s"${data.pos + 1} – ${data.description.name}")
      }
    }.observe { case (u, t) =>
      dom.window.document.title = t
      val h = getHash
      // for some reason, the leading # is not returned by getHash, when nothing follows
      if (h != u && !(u == "#" && h == "")) {
        dom.window.history.pushState(null, null, u)
        Log.JS.debug(s"pushing history '$u' was '$h' length ${dom.window.history.length}")
      }
    }

    val indexBody = index.gen()
    val frontBody = front.gen(currentData)
    val viewBody = view.gen(currentData, fitType, Navigation.navigate)

    println(s"current app state: ${currentAppState.now}")
    println(s"index body: ${indexBody.tag}")

    currentAppState.map {
      case IndexState      => Signal(indexBody)
      case FrontState(_)   => frontBody
      case ViewState(_, _) => viewBody
    }.flatten
  }

  def getDataSignal(id: Vid): Signal[Data] = {
    Signal.dynamic {
      val description = descriptions.value(id)
      val bm = bookmarks().get(description.id)
      val cont = content(description)
      Data(description, cont.value, bm.fold(0)(_.position))
    }
  }


}