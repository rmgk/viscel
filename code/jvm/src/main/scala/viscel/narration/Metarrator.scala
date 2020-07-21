package viscel.narration

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import viscel.selektiv.Narration.WrapPart
import viscel.shared.Vurl

abstract class Metarrator[T](val metarratorId: String) {
  def codec: JsonValueCodec[T]

  def toNarrator(t: T): Narrator
  def unapply(description: String): Option[Vurl]
  def wrap: WrapPart[List[T]]
}
