package viscel.selektiv

import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import viscel.narration.Narrator
import viscel.selektiv.FlowWrapper.Restriction
import viscel.selektiv.Narration.{ElementW, WrapPart}
import viscel.selektiv.Queries.extractArticle
import viscel.store.v4.DataRow

import scala.jdk.CollectionConverters._


object FlowWrapper {

  sealed class Restriction(min: Int, max: Int, val report: Report) {
    def unapply(arg: Int): Boolean = min <= arg && arg <= max
  }
  object Unique extends Restriction(1, 1, QueryNotUnique)
  object NonEmpty extends Restriction(1, Int.MaxValue, new FixedReport("query is empty"))
  object None extends Restriction(0, Int.MaxValue, new FixedReport("oh, this is strange"))
  object AtMostOne extends Restriction(0, 1, new FixedReport("query has multiple results"))

  sealed class Extractor(val extract : Element => List[DataRow.Content])
  object Extractor {
    object Image extends Extractor(e => List(extractArticle(e)))
  }

  case class Pipe(query: String, restriction: Restriction, extractors: List[Extractor]) {
    def toWrapper: Narrator.Wrapper = {
      Selection.select(query, restriction).map { elements =>
        elements.flatMap { elem: Element =>
          extractors.flatMap(_.extract(elem))
        }
      }
    }
  }


}


object Selection {

  def select(query: String, Restriction: Restriction): WrapPart[List[Element]] = {
    queryAndValidate(query) { rs =>
      rs.size() match {
        case Restriction() => Right(rs.asScala.toList)
        case other         => Left(Restriction.report)
      }
    }
  }

  def unique(query: String): WrapPart[Element] = select(query, FlowWrapper.Unique).map(_.head)
  def many(query: String): WrapPart[List[Element]] = select(query, FlowWrapper.NonEmpty)
  def all(query: String): WrapPart[List[Element]] = select(query, FlowWrapper.None)

  def queryAndValidate[R](query: String)(validate: Elements => Either[Report, R]): WrapPart[R] = {
    val trimmed = query.trim
    ElementW.map { element =>
      validate(element.select(trimmed)) match {
        case Right(elem) => elem
        case Left(r)     => throw FailedElement(query, r, element)
      }
    }
  }
}

