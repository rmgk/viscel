package viscel.selektiv

import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import viscel.selektiv.Narration.{Focus, MapW, SelectionWrap, SelectionWrapEach, SelectionWrapFlat, WrapPart}

import scala.collection.JavaConverters.iterableAsScalaIterableConverter

object Selection extends Selection(Nil)


case class Selection(pipeline: List[Element => List[Element]])  {

  /** select exactly one element */
  def unique(query: String): Selection = {
    queryAndValidate(query) {
      case rs if rs.size > 1 => throw QueryNotUnique
      case rs if rs.size < 1 => throw QueryNotMatch
      case rs => List(rs.get(0))
    }
  }
  /** select one ore more elements */
  def many(query: String): Selection = {
    queryAndValidate(query) {
      case rs if rs.size < 1 => throw QueryNotMatch
      case rs => rs.asScala.toList
    }
  }
  /** select any number of elements */
  def all(query: String): Selection = {
    queryAndValidate(query) { rs => rs.asScala.toList }
  }

  /** wrap the list of elements into a result */
  def wrap[R](fun: List[Element] => R): WrapPart[R] = SelectionWrap(this, fun)
  /** wrap the single selected element into a result */
  def wrapOne[R](fun: Element => R): WrapPart[R] =
    MapW[List[R], R](SelectionWrapEach(this, fun), _.head)
  /** wrap each element into a result and return a list of these results */
  def wrapEach[R](fun: Element => R): WrapPart[List[R]] = SelectionWrapEach(this, fun)
  /** wrap each element into a list of results, return the concatenation of these lists */
  def wrapFlat[R](fun: Element => List[R]): WrapPart[List[R]] = SelectionWrapFlat(this, fun)
  def focus[R](cont: WrapPart[List[R]]): WrapPart[List[R]] = Focus(SelectionWrapEach(this, identity), cont)



  def queryAndValidate[R](query: String)(validate: Elements => List[Element]): Selection =
    Selection(
    { element: Element =>
      try validate(element.select(query.trim))
      catch {case r: Report => throw FailedElement(query, r, element)}
    } :: pipeline
  )
}
