package viscel.narration

import io.circe.{Decoder, Encoder}
import org.jsoup.nodes.Document
import org.scalactic.{Every, Or}
import viscel.scribe.Vurl
import viscel.selection.Report

abstract class Metarrator[T <: Narrator](val id: String,
                                         val decoder: Decoder[T],
                                         val encoder: Encoder[T]) {
  def unapply(description: String): Option[Vurl]
  def wrap(document: Document): List[T] Or Every[Report]
}
