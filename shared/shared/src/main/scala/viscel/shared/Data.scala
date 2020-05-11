package viscel.shared

import rescala.extra.lattices.Lattice

import scala.collection.immutable.Map

/** The [[name]] and [[size]] of a Book. [[linked]] is true if it can still be downloaded. */
final case class Description(name: String, size: Int, linked: Boolean, timestamp: Long)
final case class ChapterPos(name: String, pos: Int)
final case class SharedImage(origin: String,
                             blob: Blob,
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

object BookmarksMap {

  type BookmarksMap = Map[Vid, Bookmark]

  def addΔ(vid: Vid, bookmark: Bookmark): BookmarksMap = Map(vid-> bookmark)


  implicit def optionLattice[A: Lattice]: Lattice[Option[A]] = {
    case (None, r)    => r
    case (l, None)    => l
    case (Some(l), Some(r)) => Some(Lattice.merge(l, r))
  }

  implicit def mapLattice[K, V: Lattice]: Lattice[Map[K, V]] = (left, right) =>
    Lattice.merge(left.keySet, right.keySet).iterator
           .flatMap { key =>
             Lattice.merge(left.get(key), right.get(key)).map(key -> _)
           }.toMap

}
