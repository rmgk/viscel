package viscel.selektiv

import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import org.scalactic.{Bad, Every, Good, Or}
import viscel.selektiv.Narration.{Focus, MapW, SelectionWrap, SelectionWrapEach, SelectionWrapFlat, WrapPart}

import scala.collection.JavaConverters.iterableAsScalaIterableConverter

object Selection extends Selection(Nil)


case class Selection(pipeline: List[Element => List[Element] Or Every[Report]])  {

  /** select exactly one element */
  def unique(query: String): Selection = {
    queryAndValidate(query) {
      case rs if rs.size > 1 => Bad(QueryNotUnique)
      case rs if rs.size < 1 => Bad(QueryNotMatch)
      case rs => Good(List(rs.get(0)))
    }
  }
  /** select one ore more elements */
  def many(query: String): Selection = {
    queryAndValidate(query) {
      case rs if rs.size < 1 => Bad(QueryNotMatch)
      case rs => Good(rs.asScala.toList)
    }
  }
  /** select any number of elements */
  def all(query: String): Selection = {
    queryAndValidate(query) { rs => Good(rs.asScala.toList) }
  }

  /** wrap the list of elements into a result */
  def wrap[R](fun: List[Element] => R Or Every[Report]): WrapPart[R] = SelectionWrap(this, fun)
  /** wrap the single selected element into a result */
  def wrapOne[R](fun: Element => R Or Every[Report]): WrapPart[R] =
    MapW[List[R], R](SelectionWrapEach(this, fun), _.head)
  /** wrap each element into a result and return a list of these results */
  def wrapEach[R](fun: Element => R Or Every[Report]): WrapPart[List[R]] = SelectionWrapEach(this, fun)
  /** wrap each element into a list of results, return the concatenation of these lists */
  def wrapFlat[R](fun: Element => List[R] Or Every[Report]): WrapPart[List[R]] = SelectionWrapFlat(this, fun)
  def focus[R](cont: WrapPart[List[R]]): WrapPart[List[R]] = Focus(SelectionWrapEach(this, Good(_)), cont)



  def queryAndValidate[R](query: String)(validate: Elements => List[Element] Or Report): Selection = Selection(
    { (element: Element) =>
      validate(element.select(query.trim)).badMap {FailedElement(query, _, element)}.accumulating
    } :: pipeline
  )
}
