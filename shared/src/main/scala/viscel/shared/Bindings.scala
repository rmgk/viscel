package viscel.shared

import io.circe.generic.auto._
import retier.registry.Binding
import retier.serializer.circe._


object Bindings {
  val descriptions = Binding[() => Iterable[Description]]("descriptions")

  val contents = Binding[String => Option[Contents]]("contents")

  val hint = Binding[(Description, Boolean) => Unit]("hint")

  type SetBookmark = Option[(Description, Int)]
  type Bookmarks = Map[String, Int]
  val bookmarks = Binding[SetBookmark => Bookmarks]("bookmarks")
}
