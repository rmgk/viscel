package viscel.shared

import kofre.base.Lattice

import scala.collection.immutable.Map

/** The [[name]] and [[size]] of a Book. [[linked]] is true if it can still be downloaded. */
final case class Description(name: String, size: Int, linked: Boolean, timestamp: Long)
final case class ChapterPos(name: String, pos: Int)
final case class SharedImage(origin: String, blob: Blob, data: Map[String, String] = Map())
final case class Contents(gallery: Seq[SharedImage], chapters: List[ChapterPos])
final case class Blob(sha1: String, mime: String)

final case class Bookmark(position: Int, timestamp: Long, sha1: Option[String] = None, origin: Option[String] = None)
object Bookmark {

  /** Newer bookmark wins. Then largest bookmark wins. */
  implicit def bookmarkLattice: Lattice[Bookmark] =
    (left, right) => {
      java.lang.Long.compare(left.timestamp, right.timestamp) match {
        case 0 => (left.sha1, right.sha1) match {
            case (None, Some(_)) => right
            case (Some(_), None) => left
            case other => left.toString.compareTo(right.toString) match {
                case 0                => left
                case less if less < 0 => right
                case _                => left
              }
          }
        case less if less < 0 => right
        case _                => left
      }
    }
}

object BookmarksMap {

  type BookmarksMap = Map[Vid, Bookmark]

  def addΔ(vid: Vid, bookmark: Bookmark): BookmarksMap = Map(vid -> bookmark)

  implicit def optionLattice[A: Lattice]: Lattice[Option[A]] = {
    case (None, r)          => r
    case (l, None)          => l
    case (Some(l), Some(r)) => Some(Lattice.merge(l, r))
  }

  implicit def mapLattice[K, V: Lattice]: Lattice[Map[K, V]] =
    (left, right) =>
      Lattice.merge(left.keySet, right.keySet).iterator
        .flatMap { key =>
          Lattice.merge(left.get(key), right.get(key)).map(key -> _)
        }.toMap

}
