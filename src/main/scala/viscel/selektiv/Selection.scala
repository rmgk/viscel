package viscel.selektiv

import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import viscel.narration.{Narrator, ViscelDefinition}
import viscel.selektiv.FlowWrapper.Restriction
import viscel.selektiv.Narration.{Append, Condition, Constant, ContextW, ElementW, WrapPart}
import viscel.selektiv.Queries._
import viscel.store.v4.DataRow

import scala.jdk.CollectionConverters._
import scala.util.Try


object FlowWrapper {


  sealed class Restriction(val min: Int, val max: Int) {
    def unapply(arg: Int): Option[RestrictionReport] = {
      if (min <= arg && arg <= max) None
      else Some(RestrictionReport(arg, this))
    }
  }
  object Restriction {
    object Unique extends Restriction(1, 1)
    object NonEmpty extends Restriction(1, Int.MaxValue)
    object None extends Restriction(0, Int.MaxValue)
    object AtMostOne extends Restriction(0, 1)
  }

  sealed class Extractor(val extract: Element => List[DataRow.Content])
  object Extractor {
    object Image extends Extractor(e => List(extractArticle(e)))
    object More extends Extractor(e => List(extractMore(e)))
    case class Parent(next: Extractor) extends Extractor(e => next.extract(e.parent()))
    case class Optional(inner: Extractor) extends Extractor(e => Try(inner.extract(e)).toOption.getOrElse(Nil))
    object MixedArchive extends Extractor(e => List(extractMixedArchive(e)))
    object Chapter extends Extractor(e => List(extractChapter(e)))
  }

  sealed class Filter(val filter: List[DataRow.Content] => List[DataRow.Content])
  object Filter {
    case class ChapterReverse(reverseInner: Boolean) extends Filter(chapterReverse(_, reverseInner))
    case class TransformUrls(replacements: List[(String, String)]) extends Filter(ViscelDefinition.transformUrls(replacements))
    case object SelectSingleNext extends Filter(contents => {
      if (contents.isEmpty) Nil
      else contents match {
        case pointers if pointers.toSet.size == 1 => pointers.headOption.toList
        case pointers => throw QueryNotUnique
      }
    })

  }

  case class Pipe(query: String,
                  restriction: Restriction,
                  extractors: List[Extractor],
                  filter: List[Filter] = Nil,
                  conditions: List[String] = Nil) {
    def toWrapper: Narrator.Wrapper = {
      val extracted = Selection.select(query, restriction).map { elements =>
        elements.flatMap { elem: Element =>
          extractors.flatMap(_.extract(elem))
        }
      }
      filter.foldLeft(extracted){(sel, fil) => sel.map(fil.filter)}
    }
  }

case class Plumbing(pipes: List[Pipe]) {
  def toWrapper: Narrator.Wrapper = {

    val condGroupd     = pipes.groupBy(_.conditions.nonEmpty)
    val conditioned    = condGroupd.getOrElse(true, Nil)
    val unconditioned  = condGroupd.getOrElse(false, Nil)
    val appendedUncond = unconditioned.map(_.toWrapper) match {
      case Nil         => Constant(Nil)
      case wrap :: Nil => wrap
      case multiple    => multiple.reduce(Append[DataRow.Content])
    }
    val appended       = conditioned.foldRight(appendedUncond) { (pipe, rest) =>
      val conditions = pipe.conditions
      val wrapper    = pipe.toWrapper
      Condition(ContextW.map(cd =>
                               conditions.exists(cd.response.location.uriString().equals) ||
                               conditions.exists(cd.request.href.uriString().equals)),
                wrapper,
                rest)
    }

    appended

  }
}



}



object Selection {

  def select(query: String, Restriction: Restriction): WrapPart[List[Element]] = {
    queryAndValidate(query) { rs =>
      rs.size() match {
        case Restriction(report) => Left(report)
        case other               => Right(rs.asScala.toList)
      }
    }
  }

  def unique(query: String): WrapPart[Element] = select(query, Restriction.Unique).map(_.head)
  def many(query: String): WrapPart[List[Element]] = select(query, Restriction.NonEmpty)
  def all(query: String): WrapPart[List[Element]] = select(query, Restriction.None)

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

