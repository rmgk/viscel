package viscel.server

import loci.registry.Registry
import rescala.default.{Evt, implicitScheduler}
import kofre.base.Lattice
import rescala.operator.Diff
import viscel.narration.Narrator
import viscel.netzi.WebRequestInterface
import viscel.shared.BookmarksMap._
import viscel.shared.Log.{Server => Log}
import viscel.shared.{Bindings, BookmarksMap, Vid}
import viscel.store.{NarratorCache, User, Users}
import rescala.default._
import viscel.Viscel
import de.rmgk.delay.Async

import scala.collection.immutable.Map
import scala.concurrent.Future

class Interactions(
    contentLoader: ContentLoader,
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

  def handleBookmarks(userid: User.Id): Signal[BookmarksMap] = {
    var user = userStore.get(userid).get
    val bookmarkMap = user.bookmarks.foldLeft[BookmarksMap](Map.empty) {
      case (bmm, (vid, bm)) =>
        Lattice.merge(bmm, BookmarksMap.addΔ(vid, bm))
    }
    val userBookmarks = Var(bookmarkMap)
    userBookmarks.change.observe {
      case Diff(prev, next) =>
        next.foreach {
          case (vid, bm) =>
            if (!prev.get(vid).contains(bm)) {
              viscel.shared.Log.Store.info(f"updating $vid to $bm for ${userid}")
              user = userStore.setBookmark(user, vid, bm)
            }
        }
    }

    userBookmarks
  }

  private def handleHint(vid: Vid, force: Boolean): Unit = {
    val nar = narratorCache.get(vid)
    if (nar.isDefined) narrationHint.fire(nar.get -> force)
    else Log.warn(s"got hint for unknown $vid")
  }
}
