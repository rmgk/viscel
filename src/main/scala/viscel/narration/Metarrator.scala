package viscel.narration

import io.circe.{Decoder, Encoder}
import viscel.narration.interpretation.NarrationInterpretation.{NarratorADT, WrapPart}
import viscel.store.Vurl

abstract class Metarrator[T](val metarratorId: String) {
  def toNarrator(t : T): NarratorADT
  def decoder: Decoder[T]
  def encoder: Encoder[T]
  def unapply(description: String): Option[Vurl]
  def wrap: WrapPart[List[T]]
}
