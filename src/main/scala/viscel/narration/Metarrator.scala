package viscel.narration

import io.circe.{Decoder, Encoder}
import viscel.netzi.Vurl
import viscel.netzi.Narration.WrapPart

abstract class Metarrator[T](val metarratorId: String) {
  def toNarrator(t : T): NarratorADT
  def decoder: Decoder[T]
  def encoder: Encoder[T]
  def unapply(description: String): Option[Vurl]
  def wrap: WrapPart[List[T]]
}
