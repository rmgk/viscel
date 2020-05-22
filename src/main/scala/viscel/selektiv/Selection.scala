package viscel.selektiv

import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import viscel.selektiv.Narration.{ElementW, WrapPart}

import scala.jdk.CollectionConverters._

object Selection {

  /** select exactly one element */
  def unique(query: String): WrapPart[Element] = {
    queryAndValidate(query) {
      case rs if rs.size > 1 => throw QueryNotUnique
      case rs if rs.size < 1 => throw QueryNotMatch
      case rs                => rs.get(0)
    }
  }
  /** select one ore more elements */
  def many(query: String): WrapPart[List[Element]] = {
    queryAndValidate(query) {
      case rs if rs.size < 1 => throw QueryNotMatch
      case rs                => rs.asScala.toList
    }
  }
  /** select any number of elements */
  def all(query: String): WrapPart[List[Element]] = {
    queryAndValidate(query) { rs => rs.asScala.toList }
  }

  def queryAndValidate[R](query: String)(validate: Elements => R): WrapPart[R] =
    ElementW.map{ element =>
      try validate(element.select(query.trim))
      catch {case r: Report => throw FailedElement(query, r, element)}
    }
}

