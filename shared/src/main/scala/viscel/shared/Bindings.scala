package viscel.shared

import io.circe.generic.auto._
import loci.registry.Binding
import loci.serializer.circe._
import loci.transmitter.IdenticallyTransmittable
import viscel.shared.BookmarksMap.BookmarksMap


object Bindings {
  type IT[V] = IdenticallyTransmittable[V]

  implicit val _Td: IT[Description] = IdenticallyTransmittable()
  implicit val _Tv: IT[Vid] = IdenticallyTransmittable()
  implicit val _Tc: IT[Contents] = IdenticallyTransmittable()
  implicit val _Tb: IT[Bookmark] = IdenticallyTransmittable()
  implicit val _Tbm: IT[BookmarksMap] = IdenticallyTransmittable()

  val descriptions = Binding[() => Iterable[Description]]("descriptions")

  val contents = Binding[Vid => Option[Contents]]("contents")

  val hint = Binding[(Description, Boolean) => Unit]("hint")

  type SetBookmark = Option[(Description, Bookmark)]
  type Bookmarks = Map[Vid, Bookmark]
  val bookmarks = Binding[SetBookmark => Bookmarks]("bookmarks")

  val bookmarksMapBindig = Binding[BookmarksMap => Unit]("bookmarksmap")

  val bookUpdate = Binding[Description => Unit]("bookUpdate")
}
