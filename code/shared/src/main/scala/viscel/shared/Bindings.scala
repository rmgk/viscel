package viscel.shared

import loci.registry.Binding
import loci.serializer.jsoniterScala._
import loci.transmitter.IdenticallyTransmittable
import viscel.shared.JsoniterCodecs._

object Bindings {
  type IT[V] = IdenticallyTransmittable[V]

  implicit val _Td: IT[Description]         = IdenticallyTransmittable()
  implicit val _Tv: IT[Vid]                 = IdenticallyTransmittable()
  implicit val _Tc: IT[Contents]            = IdenticallyTransmittable()
  implicit val _Tb: IT[Bookmark]            = IdenticallyTransmittable()
  implicit val _Tbm: IT[Map[Vid, Bookmark]] = IdenticallyTransmittable()

  val descriptions       = Binding[() => Map[Vid, Description]]("descriptions")
  val contents           = Binding[Vid => Option[Contents]]("contents")
  val hint               = Binding[(Vid, Boolean) => Unit]("hint")
  val bookmarksMapBindig = Binding[Map[Vid, Bookmark] => Unit]("bookmarksmap")
  val version            = Binding[String]("version")
}
