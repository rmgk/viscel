package viscel.shared

import loci.registry.Binding
import loci.transmitter.IdenticallyTransmittable
import viscel.shared.JsoniterLociSerializable._
import viscel.shared.JsoniterCodecs._

import scala.annotation.nowarn

object Bindings {
  type IT[V] = IdenticallyTransmittable[V]

  implicit val _Td: IT[Description]         = IdenticallyTransmittable()
  implicit val _Tv: IT[Vid]                 = IdenticallyTransmittable()
  implicit val _Tc: IT[Contents]            = IdenticallyTransmittable()
  implicit val _Tb: IT[Bookmark]            = IdenticallyTransmittable()
  implicit val _Tbm: IT[Map[Vid, Bookmark]] = IdenticallyTransmittable()

  @nowarn
  val descriptions = Binding[() => Map[Vid, Description]]("descriptions")
  @nowarn
  val contents = Binding[Vid => Option[Contents]]("contents")
  @nowarn
  val hint = Binding[(Vid, Boolean) => Unit]("hint")
  @nowarn
  val bookmarksMapBindig = Binding[Map[Vid, Bookmark] => Unit]("bookmarksmap")
  @nowarn
  val version = Binding[String]("version")
}
