package viscel.narration

import io.circe.{Decoder, Encoder}
import org.jsoup.nodes.Document
import org.scalactic.{Every, Or}
import viscel.narration.interpretation.NarrationInterpretation.NarratorADT
import viscel.scribe.Vurl
import viscel.selection.Report

abstract class Metarrator[T](val id: String) {
  def toNarrator(t : T): NarratorADT
  def decoder: Decoder[T]
  def encoder: Encoder[T]
  def unapply(description: String): Option[Vurl]
  def wrap(document: Document): List[T] Or Every[Report]
}
