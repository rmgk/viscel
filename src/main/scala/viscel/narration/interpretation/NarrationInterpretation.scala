package viscel.narration.interpretation

import io.circe.Json
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.scalactic.Accumulation._
import org.scalactic.{Bad, Every, Good, Or}
import viscel.crawl.VResponse
import viscel.narration.Narrator
import viscel.selection.{ExtractionFailed, Report, Selection}
import viscel.shared.Vid
import viscel.store.Vurl
import viscel.store.v3.Volatile
import viscel.store.v4.DataRow

import scala.util.matching.Regex


object NarrationInterpretation {

  val Log = viscel.shared.Log.Narrate


  def transformUrls(replacements: List[(String, String)])(stories: List[DataRow.Content]): List[DataRow.Content] = {

    def replaceVurl(url: Vurl): Vurl =
      replacements.foldLeft(url.uriString) {
        case (u, (matches, replace)) => u.replaceAll(matches, replace)
      }

    stories.map {
      case DataRow.Link(url, data)     => DataRow.Link(replaceVurl(url), data)
      case o                           => o
    }
  }

  def applySelection(doc: Element, sel: Selection): List[Element] Or Every[Report] = {
    sel.pipeline.reverse.foldLeft(Good(List(doc)).orBad[Every[Report]]) { (elems, sel) =>
      elems.flatMap { els: List[Element] =>
        val validated: Or[List[List[Element]], Every[Report]] = els.validatedBy(sel)
        validated.map(_.flatten)
      }
    }
  }

  case class NI(link: DataRow.Link, response: VResponse[String]) {
    def interpret[T](outerWrapper: WrapPart[T]): T Or Every[Report] = {
      val document = Jsoup.parse(response.content, response.location.uriString())
      recurse(outerWrapper)(document)
    }
    def recurse[T](wrapper: WrapPart[T])(implicit element: Element): T Or Every[Report] = {
      val res: Or[T, Every[Report]] = wrapper match {
        case ElementW                               =>
          org.scalactic.attempt(element).badMap(ExtractionFailed.apply).accumulating
        case ContextW                               => Good(ContextData(link, response))
        case Constant(t)                            => Good(t)
        case Alternative(left, right)               => recurse(left).orElse(recurse(right))
        case Condition(pred, isTrue, isFalse)       =>
          recurse(pred).flatMap(c => if (c) recurse(isTrue) else recurse(isFalse))
        case AdditionalErrors(target, augmentation) => recurse(target).badMap(augmentation)
        case SelectionWrap(sel, fun)                => applySelection(element, sel).flatMap(fun)
        case Combination(left, right, fun)          => withGood(recurse(left), recurse(right))(fun)
        case MapW(target, fun)                      => recurse(target).map(fun)
        case Focus(selection, continue)             =>
          recurse(selection).flatMap { listOfElements =>
            listOfElements.validatedBy(recurse(continue)(_)).map(_.flatten)
          }

        case JsonW                             =>
          io.circe.parser.parse(response.content)
          .fold[Json Or Report](b => Bad(ExtractionFailed(b)), Good(_))
          .accumulating
      }
      res
    }
  }


  case class NarratorADT(id: Vid, name: String, archive: List[DataRow.Content], wrap: Wrapper)
    extends Narrator {
    override def wrapper: Wrapper = wrap
  }

  type Wrapper = WrapPart[List[DataRow.Content]]

  sealed trait WrapPart[+T] {
    def map[U](fun: T => U): WrapPart[U] = MapW(this, fun)
  }

  def PolicyDecision[T](volatile: WrapPart[T], normal: WrapPart[T]) =
    Condition(ContextW.map(_.link.data.headOption.contains(Volatile.toString)), volatile, normal)

  case class Condition[T](pred: WrapPart[Boolean], isTrue: WrapPart[T], isFalse: WrapPart[T])
    extends WrapPart[T]


  case class SelectionWrap[+R](selection: Selection, fun: List[Element] => R Or Every[Report])
    extends WrapPart[R]

  def SelectionWrapEach[R](selection: Selection, fun: Element => R Or Every[Report])
  : WrapPart[List[R]] = SelectionWrap(selection, (l: List[Element]) => l.validatedBy(fun))
  def SelectionWrapFlat[R](selection: Selection, fun: Element => List[R] Or Every[Report])
  : WrapPart[List[R]] = SelectionWrap(selection,
                                      (l: List[Element]) => l.validatedBy(fun).map(_.flatten))

  case object ElementW extends WrapPart[Element]

  def Append[T](left: WrapPart[List[T]], right: WrapPart[List[T]]): WrapPart[List[T]] =
    Combination.of(left, right) { (l, r) => l ::: r }
  case class Combination[A, B, +T] private(left: WrapPart[A],
                                           right: WrapPart[B],
                                           fun: (A, B) => T)
    extends WrapPart[T]
  object Combination {
    def of[A, B, T](left: WrapPart[A], right: WrapPart[B])
                   (fun: (A, B) => T)
    : Combination[A, B, T] = Combination(left, right, fun)
  }

  case class MapW[T, +U](target: WrapPart[T], fun: T => U) extends WrapPart[U]
  object MapW {
    def of[T, U](target: WrapPart[T])(fun: T => U): MapW[T, U] = MapW(target, fun)
    def reverse[T](target: WrapPart[List[T]]): MapW[List[T], List[T]] = MapW(target, _.reverse)
  }

  case class Focus[T](selection: WrapPart[List[Element]], continue: WrapPart[List[T]])
    extends WrapPart[List[T]]
  def Decision[T](pred: Element => Boolean, isTrue: WrapPart[T], isFalse: WrapPart[T]): WrapPart[T]
  = Condition(ElementW.map(pred), isTrue, isFalse)

  case class Constant[T](c: T) extends WrapPart[T]

  case class ContextData(link: DataRow.Link, response: VResponse[String])
  // single data element extraction
  case object ContextW extends WrapPart[ContextData]
  case object JsonW extends WrapPart[Json]


  // used for vid
  case class Alternative[+T](left: WrapPart[T], right: WrapPart[T]) extends WrapPart[T]
  def LocationMatch[T](regex: Regex, isTrue: WrapPart[T], isFalse: WrapPart[T]) =
    Condition(ContextW.map(c => regex.findFirstIn(c.response.location.uriString()).isDefined),
              isTrue, isFalse)
  def TransformUrls(target: Wrapper, replacements: List[(String, String)]) =
    MapW(target, transformUrls(replacements))
  case class AdditionalErrors[+E](target: WrapPart[E], augmentation: Every[Report] => Every[Report])
    extends WrapPart[E]

}

