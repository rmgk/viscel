package viscel.shared

import retier.registry.Binding
import retier.serializer.circe._
import io.circe.generic.auto._


object Bindings {
  val contents = Binding[String => Option[Contents]]("contents")
}
