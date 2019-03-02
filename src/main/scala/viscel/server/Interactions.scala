package viscel.server

import akka.http.scaladsl.server.Route
import loci.communicator.ws.akka.WebSocketListener
import loci.registry.Registry
import rescala.default.{Evt, implicitScheduler}
import viscel.crawl.WebRequestInterface
import viscel.narration.Narrator
import viscel.shared.Bindings.SetBookmark
import viscel.shared.Log.{Server => Log}
import viscel.shared.{Bindings, Bookmark, Description, Vid}
import viscel.store.{NarratorCache, User, Users}

import scala.collection.mutable
import scala.concurrent.Future

class Interactions(contentLoader: ContentLoader, narratorCache: NarratorCache,
                   narrationHint: Evt[(Narrator, Boolean)],
                   userStore    : Users,
                   requestUtil  : WebRequestInterface
                  ) {

  def addNarratorsFrom(url: String): Future[List[Narrator]] = narratorCache.add(url, requestUtil)

  private val userSocketCache: mutable.Map[String, Route] = mutable.Map.empty

  def userSocket(user: User): Route = synchronized {
    userSocketCache.getOrElseUpdate(user.id, bindUserSocketRegistry(user))
  }

  private def bindUserSocketRegistry(user: User): Route = {
    Log.debug(s"create new websocket for $user")
    val webSocket = WebSocketListener()
    val registry = new Registry
    registry.listen(webSocket)
    registry.bind(Bindings.contents) {contentLoader.narration}
    registry.bind(Bindings.descriptions) { () => contentLoader.narrations() }
    registry.bind(Bindings.hint) {handleHint}
    registry.bind(Bindings.bookmarks) {handleBookmarks(user)}
    webSocket
  }

  private def handleBookmarks(user: User)(command: SetBookmark): Map[Vid, Bookmark] = {
    command.fold(user) { case (desc, bm) =>
      setBookmark(user, bm, desc.id)
    }.bookmarks
  }

  private def setBookmark(user: User, bm: Bookmark, colid: Vid): User = {
    if (bm.position > 0) userStore.userUpdate(user.setBookmark(colid, bm))
    else userStore.userUpdate(user.removeBookmark(colid))
  }

  private def handleHint(description: Description, force: Boolean): Unit = {
    val nar = narratorCache.get(description.id)
    if (nar.isDefined) narrationHint.fire(nar.get -> force)
    else Log.warn(s"got hint for unknown $description")
  }
}
