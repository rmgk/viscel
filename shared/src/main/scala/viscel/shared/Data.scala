package viscel.shared

import rescala.extra.deltacrdts.AddWinsSet
import rescala.extra.lattices.IdUtil.Id
import rescala.extra.lattices.Lattice

import scala.collection.immutable.Map

/** The [[name]] and [[size]] of a collection. [[unknownNarrator]] is false if it can still be downloaded. */
final case class Description(id: Vid, name: String, size: Int, unknownNarrator: Boolean)
final case class ChapterPos(name: String, pos: Int)
final case class SharedImage(origin: String,
                             blob: Option[Blob] = None,
                             data: Map[String, String] = Map())
final case class Contents(gallery: Gallery[SharedImage], chapters: List[ChapterPos])
final case class Blob(sha1: String, mime: String)

final case class Bookmark(position: Int, timestamp: Long)
object Bookmark {
  /** Newer bookmark wins. Then largest bookmark wins. */
  implicit def bookmarkLattice: Lattice[Bookmark] = (left, right) => {
      java.lang.Long.compare(left.timestamp, right.timestamp) match {
        case 0 => Bookmark(math.max(left.position, right.position), left.timestamp)
        case less if less < 0 => right
        case _ => left
      }
  }
}

case class BookmarksMap(bindings: Map[Vid, Bookmark], contains: AddWinsSet[Vid]) {
  def addΔ(vid: Vid, bookmark: Bookmark)(replicaId: Id): BookmarksMap =
    BookmarksMap(Map(vid-> bookmark), contains.add(vid, replicaId))

}
object BookmarksMap {

  def empty: BookmarksMap = BookmarksMap(Map.empty, AddWinsSet.empty)

  /** Merge contains. Then merge remaining bookmarks. */
  implicit def bookmarkMapLattice: Lattice[BookmarksMap] = (left, right) => {
    val contains = Lattice.merge(left.contains, right.contains)
    val bindings = contains.toSet.flatMap {vid =>
      (left.bindings.get(vid), right.bindings.get(vid)) match {
        case (None, Some(r)) => Some(vid -> r)
        case (Some(l), None) => Some(vid -> l)
        case (Some(l), Some(r)) => Some(vid -> Lattice.merge(l, r))
        case (None, None) => None
      }
    }.toMap
    BookmarksMap(bindings, contains)
  }

}
