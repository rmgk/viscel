package viscel.narration.interpretation

import org.jsoup.nodes.{Document, Element}
import org.scalactic.Accumulation._
import org.scalactic.{Every, Good, Or}
import viscel.narration.{Contents, Narrator}
import viscel.scribe.{ImageRef, Link, Normal, Volatile, Vurl, WebContent}
import viscel.selection.{Report, Selection}



object NarrationInterpretation {

  val Log = viscel.shared.Log.Narrate


  def transformUrls(replacements: List[(String, String)])(stories: List[WebContent]) = {

    def replaceVurl(url: Vurl): Vurl =
      replacements.foldLeft(url.uriString) {
        case (u, (matches, replace)) => u.replaceAll(matches, replace)
      }

    stories.map {
      case Link(url, policy, data) => Link(replaceVurl(url), policy, data)
      case ImageRef(url, origin, data) => ImageRef(replaceVurl(url), origin, data)
      case o => o
    }
  }

  def applySelection(doc: Element, sel: Selection): List[Element] Or Every[Report] = {
    sel.pipeline.reverse.foldLeft(Good(List(doc)).orBad[Every[Report]]){ (elems, sel) =>
      elems.flatMap { (els: List[Element]) =>
        val validated: Or[List[List[Element]], Every[Report]] = els.validatedBy(sel)
        validated.map(_.flatten)
      }
    }
  }

  def interpret[T](outerWrapper: WrapPart[T], doc: Element, link: Link): T Or Every[Report] = {
    def recurse[Ti](wrapper: WrapPart[Ti]): Ti Or Every[Report] = {
      val res: Or[Ti, Every[Report]] = wrapper match {
        case OmnipotentWrapper(narrator) => narrator(doc.ownerDocument(), link)
        case DocumentWrapper(wrap) => wrap(doc)
        case PolicyDecision(volatile, normal) => link match {
          case Link(_, Volatile, _) => recurse(volatile)
          case Link(_, Normal, _) => recurse(normal)
        }
        case TransformUrls(target, replacements) => recurse(target).map(transformUrls(replacements))
        case AdditionalErrors(target, augmentation) => recurse(target).badMap(augmentation)
        case SelectionWrap(sel, fun) => applySelection(doc, sel).flatMap(fun)
        case Combine(left, right, fun) => withGood(recurse(left), recurse(right))(fun)
        case Shuffle(target, fun) => recurse(target).map(fun)
        case LinkDataDecision(pred, isTrue, isFalse) => if (pred(link.data)) recurse(isTrue) else recurse(isFalse)
        case Focus(selection, continue) => recurse(selection).flatMap { listOfElements =>
          listOfElements.validatedBy(interpret(continue, _, link)).map(_.flatten)
        }
        case Decision(pred, isTrue, isFalse) => if (pred(doc)) recurse(isTrue) else recurse(isFalse)
        case Constant(t) => t
        case Alternative(left, right) => recurse(left).orElse(recurse(right))
      }
      Log.trace(s"$wrapper returned $res")
      res
    }
    Log.trace(s"interpreting ${doc.baseUri()} with $outerWrapper")
    recurse(outerWrapper)
  }

  case class NarratorADT(id: String, name: String, archive: List[WebContent], wrap: Wrapper) extends Narrator {
    override def wrap(doc: Document, link: Link): Contents = interpret(wrap, doc, link)
    override def wrapper: Wrapper = wrap
  }

  type Wrapper = WrapPart[List[WebContent]]

  sealed trait WrapPart[+T]
  case class OmnipotentWrapper(narrator: (Document, Link) => Contents) extends Wrapper
  case class DocumentWrapper[T](wrapPage: Element => T Or Every[Report]) extends WrapPart[T]
  case class PolicyDecision[T](volatile: WrapPart[T], normal: WrapPart[T]) extends WrapPart[T]
  case class LinkDataDecision[T](pred: List[String] => Boolean, isTrue: WrapPart[T], isFalse: WrapPart[T]) extends WrapPart[T]

  case class TransformUrls(target: Wrapper, replacements: List[(String, String)]) extends Wrapper
  case class AdditionalErrors[+E](target: WrapPart[E], augmentation: Every[Report] => Every[Report]) extends WrapPart[E]

  case class SelectionWrap[+R](selection: Selection, fun: List[Element] => R Or Every[Report]) extends WrapPart[R]

  def SelectionWrapEach[R](selection: Selection, fun: Element => R Or Every[Report]): WrapPart[List[R]] = SelectionWrap(selection,
    (l: List[Element]) => l.validatedBy(fun)
  )
  def SelectionWrapFlat[R](selection: Selection, fun: Element => List[R] Or Every[Report]): WrapPart[List[R]] = SelectionWrap(selection,
    (l: List[Element]) => l.validatedBy(fun).map(_.flatten)
  )

  def Append[T](left: WrapPart[List[T]], right: WrapPart[List[T]]): WrapPart[List[T]] = Combine.of(left, right){ (l, r) => l ::: r }
  case class Combine[A, B, +T](left: WrapPart[A], right: WrapPart[B], fun: (A, B) => T) extends WrapPart[T]
  object Combine {
    def of[A, B, T](left: WrapPart[A], right: WrapPart[B])(fun: (A, B) => T): Combine[A, B, T] = Combine(left, right, fun)
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

}

