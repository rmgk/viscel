package viscel.selektiv

import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import viscel.selektiv.Narration.{ElementW, Focus, WrapPart}

import scala.jdk.CollectionConverters._

object Selection {

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

  def queryAndValidate[R](query: String)(validate: Elements => List[Element]): Selection =
    Selection(
      { element: Element =>
        try validate(element.select(query.trim))
        catch {case r: Report => throw FailedElement(query, r, element)}
      } :: Nil
      )
}


case class Selection(pipeline: List[Element => List[Element]])  {




  /** wrap the list of elements into a result */
  def wrap[R](fun: List[Element] => R): WrapPart[R] = ElementW.map(e => fun(applyTo(e)))
  /** wrap the single selected element into a result */
  def wrapOne[R](fun: Element => R): WrapPart[R] =
    ElementW.map(e => fun(applyTo(e).head))
  /** wrap each element into a result and return a list of these results */
  def wrapEach[R](fun: Element => R): WrapPart[List[R]] = ElementW.map(e => applyTo(e).map(fun))
  /** wrap each element into a list of results, return the concatenation of these lists */
  def wrapFlat[R](fun: Element => List[R]): WrapPart[List[R]] = ElementW.map(e => applyTo(e).flatMap(fun))
  def focus[R](cont: WrapPart[List[R]]): WrapPart[List[R]] = Focus(ElementW.map(e => applyTo(e)), cont)



  def applyTo(element: Element): List[Element] = {
    pipeline.reverse.foldLeft(List(element)) { (elems, sel) => elems.flatMap(sel) }
  }


}
