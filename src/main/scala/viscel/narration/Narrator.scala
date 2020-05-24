package viscel.narration

import viscel.narration.narrators.{Mangadex, Tapas, WebToons}
import viscel.selektiv.FlowWrapper.Plumbing
import viscel.selektiv.Narration.WrapPart
import viscel.shared.Vid
import viscel.store.v4.DataRow

object Narrator {
  type Wrapper = WrapPart[List[DataRow.Content]]
  val metas: List[Metarrator[_]] = List(Mangadex, WebToons, Tapas)
}

case class FlowNarrator(override val id: Vid,
                        override val name: String,
                        override val archive: List[DataRow.Content],
                        plumbing: Plumbing) extends Narrator(id, name, archive) {
  override val wrapper: WrapPart[List[DataRow.Content]] = plumbing.toWrapper
}

/** Describes the structure of a web collection */
abstract class Narrator(
                         /** [[id]] of each [[Narrator]] is globally unique,
                          * and used to lookup the [[Narrator]] and the result in all data structures.
                          * Typically something like XX_WonderfulCollection where XX is some grouping string,
                          * and WonderfulCollection the normalized [[name]] of the collection. */
                         val id: Vid,
                         /** name of the collection */
                         val name: String,
                         /** Starting link, or multiple links in case the structure is very static */
                         val archive: List[DataRow.Content]
                       ) {

  /** the complicated part */
  val wrapper: WrapPart[List[DataRow.Content]]

  /** Override equals to store in Sets.
   * There never should be two equal Narrators, but things break if there were. */
  final override def equals(other: Any): Boolean = other match {
    case o: Narrator => id == o.id
    case _           => false
  }
  final override def hashCode: Int = id.hashCode
  override def toString: String = s"$id($name)"
}


