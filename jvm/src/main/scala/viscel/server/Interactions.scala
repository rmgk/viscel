package viscel.server

import de.rmgk.delay.Async
import kofre.base.Lattice
import loci.registry.Registry
import rescala.default.*
import rescala.operator.Diff
import viscel.Viscel
import viscel.narration.Narrator
import viscel.netzi.WebRequestInterface
import viscel.shared.BookmarksMap.*
import viscel.shared.Log.Server as Log
import viscel.shared.{Bindings, BookmarksMap, Vid}
import viscel.store.{NarratorCache, User, Users}

import scala.collection.immutable.Map

class Interactions(
    val contentLoader: ContentLoader,
    narratorCache: NarratorCache,
    narrationHint: Evt[(Narrator, Boolean)],
    userStore: Users,
    requestUtil: WebRequestInterface
) {

  def addNarratorsFrom(url: String): Async[Any, List[Narrator]] = narratorCache.add(url, requestUtil)

  def authenticate(username: String, password: String): Option[User] = {
    Log.trace(s"login: $username")
    if (username.matches("\\w+")) {
      userStore.getOrAddFirstUser(username, User(username, password, admin = true, Map()))
        .filter(_.password == password)
    } else None
  }

  def bindGlobalData(registry: Registry): Unit = {
    registry.bind(Bindings.contents) { contentLoader.contents }
    registry.bind(Bindings.descriptions) { () => contentLoader.descriptions() }
    registry.bind(Bindings.hint) { handleHint }
    registry.bind(Bindings.version)(Viscel.version)
  }

  def handleBookmarks(userid: User.Id, update: BookmarksMap): BookmarksMap = {
    var user = userStore.get(userid).get
    val merged = user.bookmarks merge update
    userStore.setBookmarks(user, merged)
    merged
  }

  def handleHint(vid: Vid, force: Boolean): Unit = {
    val nar = narratorCache.get(vid)
    if (nar.isDefined) narrationHint.fire(nar.get -> force)
    else Log.warn(s"got hint for unknown $vid")
  }
}
