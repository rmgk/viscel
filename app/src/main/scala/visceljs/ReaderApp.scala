package visceljs

import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import org.scalajs.dom
import org.scalajs.dom.experimental.URL
import org.scalajs.dom.html
import org.scalajs.dom.raw.HashChangeEvent
import rescala.default._
import scalatags.JsDom.TypedTag
import viscel.shared.{Bookmark, Contents, Description, Log, Vid}
import visceljs.AppState.{FrontState, IndexState, ViewState}
import visceljs.Navigation.{Mode, Next, Position, Prev, navigationEvents}
import visceljs.render.{FitType, Front, Index, Snippets, View}
import visceljs.storage.Storing

import scala.collection.immutable.Map

class ReaderApp(content: Vid => Signal[Option[Contents]],
                val descriptions: Signal[Map[Vid, Description]],
                val bookmarks: Signal[Map[Vid, Bookmark]]
               ) {


  def getHash(): String = {
    dom.window.location.hash
  }


  def makeBody(index: Index, front: Front, view: View): Signal[Option[TypedTag[html.Body]]] = {


    val hashChange: Event[HashChangeEvent] =
      Events.fromCallback[HashChangeEvent](dom.window.onhashchange = _).event
    hashChange.observe(hc => Log.JS.debug(s"hash change event: ${hc.oldURL} -> ${hc.newURL}"))

    val targetStates = hashChange.map(hc => AppState.parse(new URL(hc.newURL).hash))

    val currentTargetAppState: Signal[AppState] = targetStates.fold(AppState.parse(getHash)) { case (_, next) => next }

    val setCurrentPostition: Event[Int] = currentTargetAppState.changed.collect { case ViewState(_, pos) => pos }


    val currentID: Signal[Option[Vid]] = currentTargetAppState.map {
      case FrontState(id)   => Some(id)
      case ViewState(id, _) => Some(id)
      case _                => None
    }

    val description = Signal {currentID.value.flatMap(descriptions.value.get)}

    val contents = Signal {currentID.value.map(content)}.flatten.map(_.flatten)

    val bookmark = Signal[Bookmark] {currentID.value.flatMap(bookmarks.value.get).getOrElse(Bookmark(0, 0, None, None))}

    val maxPosition = contents.map(_.map(_.gallery.size).getOrElse(0)).changed

    val currentPosition = Events.foldAll(Position(0, 0))(acc => Seq(
      setCurrentPostition >> acc.set,
      navigationEvents >> acc.mov,
      maxPosition >> acc.limit
      ))


    val normalizedAppState: Signal[AppState] = currentTargetAppState.map {_.transformPos(_ => currentPosition.value.cur)}

    normalizedAppState.observe(fireImmediately = false, onValue = { as =>
      val nextHash    = as.urlhash
      val currentHash = getHash().drop(1)
      if (nextHash != currentHash) {
        Log.JS.debug(s"pushing ${nextHash} was $currentHash")
        dom.window.history.pushState(null, null, "#" + as.urlhash)
      }
    })


    navigationEvents.map(e => (e, contents.value, currentPosition.value.cur)).observe { case (ev, con, pos) =>
      if (ev == Prev || ev == Next) {
        dom.window.scrollTo(0, 0)
      }
      /*val pregen =*/ con.flatMap(_.gallery.lift(pos + 1)).foreach { asst => Snippets.asset(asst).render }

    }

    Signal.static {
      currentTargetAppState.value match {
        case IndexState      => Some("Viscel")
        case FrontState(_)   => description.value.map(_.name)
        case ViewState(_, _) =>
          description.value.map(_.name).map { name =>
            s"${currentPosition.value.cur + 1} - $name"
          }
      }
    }.observe {
      case Some(newTitle) => dom.window.document.title = newTitle
      case None           =>
    }


    val fitType: Signal[FitType] = {
      Storing.storedAs[FitType]("fitType", default = FitType.W) { init =>
        navigationEvents.collect { case Mode(t) => t }.latest[FitType](init)
      }(JsonCodecMaker.make)
    }

    val indexBody = index.gen()
    val frontBody = Signal {
      for {
        vid <- currentID.value
        desc <- description.value
        cont <- contents.value} yield {
        front.gen(vid, desc, cont, bookmark.value)
      }
    }
    val viewBody  =
      Signal {
        for {
          vid <- currentID.value
          cont <- contents.value} yield {
          view.gen(vid, currentPosition.value, bookmark.value, cont, fitType, Navigation.navigate)
        }
      }

    currentTargetAppState.map {
      case IndexState      => Signal(Some(indexBody))
      case FrontState(_)   => frontBody
      case ViewState(_, _) => viewBody
    }.flatten
  }


}
