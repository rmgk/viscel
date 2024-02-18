package viscel.shared

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import loci.registry.Binding
import loci.serializer.jsoniterScala.*
import loci.transmitter.IdenticallyTransmittable
import viscel.shared.JsoniterCodecs.*

object Bindings {
  type IT[V] = IdenticallyTransmittable[V]

  implicit val _Td: IT[Description]         = IdenticallyTransmittable()
  implicit val _Tv: IT[Vid]                 = IdenticallyTransmittable()
  implicit val _Tc: IT[Contents]            = IdenticallyTransmittable()
  implicit val _Tb: IT[Bookmark]            = IdenticallyTransmittable()
  implicit val _Tbm: IT[Map[Vid, Bookmark]] = IdenticallyTransmittable()


  given JsonValueCodec[String] = JsonCodecMaker.make[String]

  val descriptions       = Binding[() => Map[Vid, Description]]("descriptions")
  val contents           = Binding[Vid => Option[Contents]]("contents")
  val hint               = Binding[(Vid, Boolean) => Unit]("hint")
  val bookmarksMapBindig = Binding[Map[Vid, Bookmark] => Unit]("bookmarksmap")
  val version            = Binding[String]("version")
}
