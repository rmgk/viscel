package visceljs.connection

import kofre.base.Lattice
import loci.registry.Registry
import rescala.default.*
import rescala.extra.distributables.LociDist
import viscel.shared.JsoniterCodecs.*
import viscel.shared.{Bindings, Bookmark, BookmarksMap, Vid}
import visceljs.storage.Storing

class BookmarkManager(registry: Registry) {
  val setBookmark = Evt[(Vid, Bookmark)]()
  val bookmarks =
    Storing.storedAs("bookmarksmap", Map.empty[Vid, Bookmark]) { initial =>
      setBookmark.fold(initial) {
        case (map, (vid, bm)) =>
          Lattice.merge(map, BookmarksMap.addΔ(vid, bm))
      }
    }

  LociDist.distribute(bookmarks, registry)(Bindings.bookmarksMapBindig)

}
