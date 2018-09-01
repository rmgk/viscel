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
import viscel.store.{ImageRef, Link, Normal, Volatile, Vurl, WebContent}



object NarrationInterpretation {

  val Log = viscel.shared.Log.Narrate


  def transformUrls(replacements: List[(String, String)])(stories: List[WebContent]): List[WebContent] = {

    def replaceVurl(url: Vurl): Vurl =
      replacements.foldLeft(url.uriString) {
        case (u, (matches, replace)) => u.replaceAll(matches, replace)
      }

    stories.map {
      case Link(url, policy, data)     => Link(replaceVurl(url), policy, data)
      case ImageRef(url, origin, data) => ImageRef(replaceVurl(url), origin, data)
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

  case class NI(link: Link, response: VResponse[String]) {
    def interpret[T](outerWrapper: WrapPart[T]): T Or Every[Report] = {
      val document = Jsoup.parse(response.content, response.location.uriString())
      recurse(outerWrapper)(document)
    }
    def recurse[T](wrapper: WrapPart[T])(implicit element: Element): T Or Every[Report] = {
      val res: Or[T, Every[Report]] = wrapper match {
        case ElementWrapper(wrap)                   => wrap(element)
        case PolicyDecision(volatile, normal)       => link.policy match {
          case Volatile => recurse(volatile)
          case Normal   => recurse(normal)
        }
        case TransformUrls(target, replacements)    => recurse(target).map(transformUrls(replacements))
        case AdditionalErrors(target, augmentation) => recurse(target).badMap(augmentation)
        case SelectionWrap(sel, fun)                => applySelection(element, sel).flatMap(fun)
        case Combination(left, right, fun)          => withGood(recurse(left), recurse(right))(fun)
        case Shuffle(target, fun)                   => recurse(target).map(fun)
        case LinkDecision(pred, isTrue, isFalse)    => if (pred(link)) recurse(isTrue) else recurse(isFalse)
        case Focus(selection, continue)             => recurse(selection).flatMap { listOfElements =>
          listOfElements.validatedBy(recurse(continue)(_)).map(_.flatten)
        }
        case Decision(pred, isTrue, isFalse)        => if (pred(element)) recurse(isTrue) else recurse(isFalse)
        case Constant(t)                            => t
        case Alternative(left, right)               => recurse(left).orElse(recurse(right))
        case JsonWrapper(fun)                       =>
          io.circe.parser.parse(response.content)
          .fold[Json Or Report](b => Bad(ExtractionFailed(b)), Good(_))
          .accumulating
          .flatMap(fun)
        case Location                               => Good(response.location)
      }
      res
    }
  }



  def strNarratorADT(id: String, name: String, archive: List[WebContent], wrap: Wrapper): NarratorADT =
    NarratorADT(Vid.from(id), name, archive, wrap)

  case class NarratorADT(id: Vid, name: String, archive: List[WebContent], wrap: Wrapper) extends Narrator {
    override def wrapper: Wrapper = wrap
  }

  type Wrapper = WrapPart[List[WebContent]]

  sealed trait WrapPart[+T]
  case class ElementWrapper[T](wrapPage: Element => T Or Every[Report]) extends WrapPart[T]
  case class PolicyDecision[T](volatile: WrapPart[T], normal: WrapPart[T]) extends WrapPart[T]
  case class LinkDecision[T](pred: Link => Boolean, isTrue: WrapPart[T], isFalse: WrapPart[T]) extends WrapPart[T]
  def LinkDataDecision[T](pred: List[String] => Boolean, isTrue: WrapPart[T], isFalse: WrapPart[T]) =
    LinkDecision(p => pred(p.data), isTrue, isFalse)

  case class TransformUrls(target: Wrapper, replacements: List[(String, String)]) extends Wrapper
  case class AdditionalErrors[+E](target: WrapPart[E], augmentation: Every[Report] => Every[Report]) extends WrapPart[E]

  case class SelectionWrap[+R](selection: Selection, fun: List[Element] => R Or Every[Report]) extends WrapPart[R]

  def SelectionWrapEach[R](selection: Selection, fun: Element => R Or Every[Report]): WrapPart[List[R]] = SelectionWrap(selection,
    (l: List[Element]) => l.validatedBy(fun)
  )
  def SelectionWrapFlat[R](selection: Selection, fun: Element => List[R] Or Every[Report]): WrapPart[List[R]] = SelectionWrap(selection,
    (l: List[Element]) => l.validatedBy(fun).map(_.flatten)
  )

  def Append[T](left: WrapPart[List[T]], right: WrapPart[List[T]]): WrapPart[List[T]] = Combination.of(left, right){ (l, r) => l ::: r }
  case class Combination[A, B, +T](left: WrapPart[A], right: WrapPart[B], fun: (A, B) => T) extends WrapPart[T]
  object Combination {
    def of[A, B, T](left: WrapPart[A], right: WrapPart[B])(fun: (A, B) => T): Combination[A, B, T] = Combination(left, right, fun)
  }
  case class Alternative[+T](left: WrapPart[T], right: WrapPart[T]) extends WrapPart[T]


  case class Shuffle[T, +U](target: WrapPart[T], fun: T => U) extends WrapPart[U]
  object Shuffle {
    def of[T, U](target: WrapPart[T])(fun: T => U): Shuffle[T, U] = Shuffle(target, fun)
    def reverse[T](target: WrapPart[List[T]]): Shuffle[List[T], List[T]] = Shuffle(target, _.reverse)
  }

  case class Focus[T](selection: WrapPart[List[Element]], continue: WrapPart[List[T]]) extends WrapPart[List[T]]
  case class Decision[T](pred: Element => Boolean, isTrue: WrapPart[T], isFalse: WrapPart[T]) extends WrapPart[T]

  case class Constant[T](c: T Or Every[Report]) extends WrapPart[T]

  case class JsonWrapper[T](fun: Json => T Or Every[Report]) extends WrapPart[T]
  case object Location extends WrapPart[Vurl]

}

