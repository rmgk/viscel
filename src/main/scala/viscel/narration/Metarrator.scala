package viscel.narration

import io.circe.{Decoder, Encoder}
import viscel.selektiv.Narration.WrapPart
import viscel.shared.Vurl

abstract class Metarrator[T](val metarratorId: String) {
  def toNarrator(t : T): Narrator
  def decoder: Decoder[T]
  def encoder: Encoder[T]
  def unapply(description: String): Option[Vurl]
  def wrap: WrapPart[List[T]]
}
