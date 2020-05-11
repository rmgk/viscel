package viscel.narration

import viscel.narration.Narrator.Wrapper
import viscel.selektiv.Narration.WrapPart
import viscel.shared.Vid
import viscel.store.v4.DataRow

object Narrator {
  type Wrapper = WrapPart[List[DataRow.Content]]
}

/** Describes the structure of a web collection */
trait Narrator {
  /** [[id]] of each [[Narrator]] is globally unique,
    * and used to lookup the [[Narrator]] and the result in all data structures.
    * Typically something like XX_WonderfulCollection where XX is some grouping string,
    * and WonderfulCollection the normalized [[name]] of the collection. */
  def id: Vid
  /** name of the collection */
  def name: String

  /** Starting link, or multiple links in case the structure is very static */
  def archive: List[DataRow.Content]

  /** Interpret to wraps a [[org.jsoup.nodes.Document]] */
  def wrapper: Wrapper

  /** Override equals to store in Sets.
    * There never should be two equal Narrators, but things break if there were. */
  final override def equals(other: Any): Boolean = other match {
    case o: Narrator => id == o.id
    case _ => false
  }
  final override def hashCode: Int = id.hashCode
  override def toString: String = s"$id($name)"
}


