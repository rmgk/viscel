package visceljs

import org.scalajs.dom
import org.scalajs.dom.experimental.URL
import org.scalajs.dom.html
import org.scalajs.dom.raw.HashChangeEvent
import rescala.default._
import rescala.reactives.RExceptions.EmptySignalControlThrowable
import scalatags.JsDom.TypedTag
import viscel.shared.{Bookmark, Contents, Description, Log, Vid}
import visceljs.AppState.{FrontState, IndexState, ViewState}
import visceljs.Navigation.{Mode, Next, Prev, navigationEvents}
import visceljs.render.{FitType, Front, Index, Snippets, View}
import visceljs.storage.Storing

import scala.collection.immutable.Map

class ReaderApp(content: Vid => Signal[Contents],
                val descriptions: Signal[Map[Vid, Description]],
                val bookmarks: Signal[Map[Vid, Bookmark]]
               ) {


  def getHash(): String = {
    dom.window.location.hash
  }


  def makeBody(index: Index, front: Front, view: View, manualStates: Event[AppState]): Signal[TypedTag[html.Body]] = {


    val hashChange: Event[HashChangeEvent] =
      Events.fromCallback[HashChangeEvent](dom.window.onhashchange = _).event
    hashChange.observe(hc => Log.JS.debug(s"hash change event: ${hc.oldURL} -> ${hc.newURL}"))

    val hashBasedStates = hashChange.map(hc => AppState.parse(new URL(hc.newURL).hash): @unchecked)


    val targetStates: Event[AppState] = hashBasedStates || manualStates


    val currentAppState: Signal[AppState] = Events.foldAll(AppState.parse(getHash)) { current =>
      Seq(
        targetStates >> { p => p },
        navigationEvents >> {
          case Prev    => current.transformPos(pos => math.max(pos - 1, 0))
          case Next    => current.transformPos(_ + 1)
          case Mode(_) => current
        }
        )
    }

    val currentPosition: Signal[Int] = currentAppState.map {
      case IndexState         => 1
      case FrontState(id)     => 1
      case ViewState(id, pos) => pos
    }

    val currentData: Signal[Data] = {
      currentAppState.map {
        case FrontState(id)   => id
        case ViewState(id, _) => id
        case _                => throw EmptySignalControlThrowable
      }.map(getDataSignal).flatten.map {
        _.atPos(currentPosition.value)
      }
    }

    val normalizedAppState: Signal[AppState] =
      currentData.map { data =>
        if (data.gallery.size > 0) currentAppState.value.transformPos(_ => data.pos)
        else currentAppState.value
      }.withDefault(IndexState)

    normalizedAppState.observe(fireImmediately = false, onValue = { as =>
      val nextHash    = as.urlhash
      val currentHash = getHash().drop(1)
      if (nextHash != currentHash) {
        Log.JS.debug(s"pushing ${nextHash} was $currentHash")
        dom.window.history.pushState(null, null, "#" + as.urlhash)
      }
    })


    navigationEvents.map(e => e -> currentData()).observe { case (ev, data) =>
      if (ev == Prev || ev == Next) {
        dom.window.scrollTo(0, 0)
      }
      /*val pregen =*/ data.gallery.next(1).get.map(asst => Snippets.asset(asst, data).render)

    }

    Signal.static {
      currentAppState.value match {
        case IndexState      => "Viscel"
        case FrontState(_)   => currentData.value.description.name
        case ViewState(_, _) =>
          val data = currentData.value
          s"${data.pos + 1} – ${data.description.name}"
      }
    }.observe { case (newTitle) =>
      dom.window.document.title = newTitle
    }


    val fitType: Signal[FitType] = Storing.storedAs[FitType]("fitType", default = FitType.W) { init =>
      navigationEvents.collect { case Mode(t) => t }.latest[FitType](init)
    }

    val indexBody = index.gen()
    val frontBody = front.gen(currentData)
    val viewBody  = view.gen(currentData, fitType, Navigation.navigate)

    println(s"current app state: ${currentAppState.now}")
    println(s"index body: ${indexBody.tag}")

    currentAppState.map {
      case IndexState      => Signal(indexBody)
      case FrontState(_)   => frontBody
      case ViewState(_, _) => viewBody
    }.flatten
  }





  def getDataSignal(id: Vid): Signal[Data] = {
    val cont = content(id)
    Signal.dynamic {
      val description = descriptions.value(id)
      val bm          = bookmarks.value.get(id)
      Data(id, description, cont.value, bm.fold(0)(_.position))
    }
  }


}
