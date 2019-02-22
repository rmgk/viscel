package viscel.shared

import io.circe.generic.auto._
import loci.registry.Binding
import loci.serializer.circe._


object Bindings {
  val descriptions = Binding[() => Iterable[Description]]("descriptions")

  val contents = Binding[Vid => Option[Contents]]("contents")

  val hint = Binding[(Description, Boolean) => Unit]("hint")

  type SetBookmark = Option[(Description, Bookmark)]
  type Bookmarks = Map[Vid, Bookmark]
  val bookmarks = Binding[SetBookmark => Bookmarks]("bookmarks")

  val bookUpdate = Binding[Description => Unit]("bookUpdate")
}
