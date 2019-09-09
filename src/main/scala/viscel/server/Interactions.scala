package viscel.server

import loci.communicator.Listener
import loci.communicator.ws.akka.{WS, WebSocketListener, WebSocketRoute}
import loci.registry.Registry
import rescala.default.{Evt, implicitScheduler}
import viscel.narration.Narrator
import viscel.shared.Log.{Server => Log}
import viscel.shared.{Bindings, BookmarksMap, Description}
import viscel.store.{NarratorCache, User, Users}
import cats.syntax.eq._
import cats.instances.string._
import rescala.extra.lattices.Lattice
import rescala.reactives.Signals.Diff
import viscel.netzi.WebRequestInterface
import rescala.default._
import rescala.extra.distributables.LociDist

import scala.collection.immutable.Map
import scala.collection.mutable
import scala.concurrent.Future

class Interactions(contentLoader: ContentLoader, narratorCache: NarratorCache,
                   narrationHint: Evt[(Narrator, Boolean)],
                   userStore    : Users,
                   requestUtil  : WebRequestInterface
                  ) {

  def addNarratorsFrom(url: String): Future[List[Narrator]] = narratorCache.add(url, requestUtil)

  def authenticate(username: String, password: String): Option[User] = {
    Log.trace(s"login: $username")
    if (username.matches("\\w+")) {
      userStore.getOrAddFirstUser(username, User(username, password, admin = true, Map()))
        .filter(_.password === password)
    }
    else None
  }

  type WsRoute = Listener[WS] with WebSocketRoute

  private val userSocketCache: mutable.Map[User.Id, WsRoute] = mutable.Map.empty

  def userSocket(userid: User.Id): WsRoute = synchronized {
    userSocketCache.getOrElseUpdate(userid, bindUserSocketRegistry(userid))
  }

  private def bindUserSocketRegistry(userid: User.Id): WsRoute = {
    Log.debug(s"create new websocket for $userid")
    val webSocket = WebSocketListener()
    val registry = new Registry
    registry.listen(webSocket)
    registry.bind(Bindings.contents) {contentLoader.contents}
    registry.bind(Bindings.descriptions) { () => contentLoader.descriptions() }
    registry.bind(Bindings.hint) {handleHint}
    LociDist.distribute(handleBookmarks(userid), registry)(Bindings.bookmarksMapBindig)
    webSocket
  }

  private def handleBookmarks(userid: User.Id): Signal[BookmarksMap] = {
    val user = userStore.get(userid).get
    val bookmarkMap = user.bookmarks.foldLeft(BookmarksMap.empty){case (bmm, (vid, bm)) =>
      Lattice.merge(bmm, bmm.addΔ(vid, bm)(userid))
    }
    val userBookmarks = Var(bookmarkMap)
    userBookmarks.map(_.bindings).change.observe{ case Diff(prev, next) =>
      next.foreach{ case (vid, bm) =>
        if (!prev.get(vid).contains(bm)) userStore.setBookmark(user, vid, bm)
      }
    }

    userBookmarks
  }

  private def handleHint(description: Description, force: Boolean): Unit = {
    val nar = narratorCache.get(description.id)
    if (nar.isDefined) narrationHint.fire(nar.get -> force)
    else Log.warn(s"got hint for unknown $description")
  }
}
