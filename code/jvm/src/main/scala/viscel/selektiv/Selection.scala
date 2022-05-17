package viscel.selektiv

import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import viscel.narration.{Narrator, ViscelDefinition}
import viscel.selektiv.FlowWrapper.Extractor.OptionalParentMore
import viscel.selektiv.FlowWrapper.Restriction
import viscel.selektiv.Narration.{Append, Condition, Constant, ContextW, ElementW, WrapPart}
import viscel.selektiv.Queries._
import viscel.shared.DataRow

import scala.jdk.CollectionConverters._

object FlowWrapper {

  sealed trait Restriction {

    lazy val (min, max) = this match {
      case Restriction.Unique    => (1, 1)
      case Restriction.NonEmpty  => (1, Int.MaxValue)
      case Restriction.None      => (0, Int.MaxValue)
      case Restriction.AtMostOne => (0, 1)
    }

    def unapply(arg: Int): Option[RestrictionReport] = {
      if (min <= arg && arg <= max) None
      else Some(RestrictionReport(arg, this))
    }
  }
  object Restriction {
    object Unique    extends Restriction
    object NonEmpty  extends Restriction
    object None      extends Restriction
    object AtMostOne extends Restriction
  }

  sealed trait Extractor {
    lazy val extract: Element => List[DataRow.Content] = this match {
      case Extractor.Image(attribute) => e => List(imageFromAttribute(e, attribute))
      case Extractor.More => e =>
          List(extractMore(e))
        //case Extractor.Parent(next)    => e => next.extract(e.parent())
      //case Extractor.Optional(inner) => e => Try(inner.extract(e)).toOption.getOrElse(Nil)
      case OptionalParentMore     => extractParentMore
      case Extractor.MixedArchive => e => List(extractMixedArchive(e))
      case Extractor.Chapter      => e => List(extractChapter(e))
    }
  }
  object Extractor {
    case class Image(attribute: Option[String] = None) extends Extractor
    object More                                        extends Extractor
    object OptionalParentMore                          extends Extractor
    object MixedArchive                                extends Extractor
    object Chapter                                     extends Extractor
  }

  sealed trait Filter {
    lazy val filter: List[DataRow.Content] => List[DataRow.Content] = this match {
      case Filter.ChapterReverse(reverseInner) => chapterReverse(_, reverseInner)
      case Filter.TransformUrls(replacements)  => ViscelDefinition.transformUrls(replacements)
      case Filter.SelectSingleNext => contents => {
          if (contents.isEmpty) Nil
          else
            contents match {
              case pointers if pointers.toSet.size == 1 => pointers.headOption.toList
              case pointers                             => throw QueryNotUnique
            }
        }
    }
  }
  object Filter {
    case class ChapterReverse(reverseInner: Boolean)               extends Filter
    case class TransformUrls(replacements: List[(String, String)]) extends Filter
    case object SelectSingleNext                                   extends Filter
  }

  case class Pipe(
      query: String,
      restriction: Restriction,
      extractors: List[Extractor],
      filter: List[Filter] = Nil,
      conditions: List[String] = Nil
  ) {
    def toWrapper: Narrator.Wrapper = {
      val extracted = Selection.select(query, restriction).map { elements =>
        elements.flatMap { (elem: Element) =>
          extractors.flatMap(_.extract(elem))
        }
      }
      filter.foldLeft(extracted) { (sel, fil) => sel.map(fil.filter) }
    }
  }

  case class Plumbing(pipes: List[Pipe]) {
    def toWrapper: Narrator.Wrapper = {

      val condGroupd    = pipes.groupBy(_.conditions.nonEmpty)
      val conditioned   = condGroupd.getOrElse(true, Nil)
      val unconditioned = condGroupd.getOrElse(false, Nil)
      val appendedUncond = unconditioned.map(_.toWrapper) match {
        case Nil         => Constant(Nil)
        case wrap :: Nil => wrap
        case multiple    => multiple.reduce(Append[DataRow.Content])
      }
      val appended = conditioned.foldRight(appendedUncond) { (pipe, rest) =>
        val conditions = pipe.conditions
        val wrapper    = pipe.toWrapper
        Condition(
          ContextW.map(cd =>
            conditions.exists(cd.response.location.uriString().equals) ||
              conditions.exists(cd.request.href.uriString().equals)
          ),
          wrapper,
          rest
        )
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

  def unique(query: String): WrapPart[Element]     = select(query, Restriction.Unique).map(_.head)
  def many(query: String): WrapPart[List[Element]] = select(query, Restriction.NonEmpty)
  def all(query: String): WrapPart[List[Element]]  = select(query, Restriction.None)

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
