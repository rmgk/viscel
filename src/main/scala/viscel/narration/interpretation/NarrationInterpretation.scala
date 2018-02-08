package viscel.narration.interpretation

import org.jsoup.nodes.{Document, Element}
import org.scalactic.Accumulation._
import org.scalactic.{Every, Or}
import viscel.narration.{Contents, Narrator}
import viscel.scribe.{ImageRef, Link, Normal, Volatile, Vurl, WebContent}
import viscel.selection.{Report, Selection}



object NarrationInterpretation {


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

  def applySelection[R](doc: Element, sel: Selection): List[R] Or Every[Report] = ???

  def interpret[T](outerWrapper: WrapPart[T], doc: Element, link: Link): T Or Every[Report] = {
    def recurse[Ti](wrapper: WrapPart[Ti]): Ti Or Every[Report] =
      wrapper match {
        case OmnipotentWrapper(narrator) => narrator(doc.ownerDocument(), link)
        case DocumentWrapper(wrap) => wrap(doc)
        case PolicyDecision(volatile, normal) => link match {
          case Link(_, Volatile, _) => recurse(volatile)
          case Link(_, Normal, _) => recurse(normal)
        }
        case TransformUrls(target, replacements) => recurse(target).map(transformUrls(replacements))
        case AdditionalErrors(target, augmentation) => recurse(target).badMap(augmentation)
        case SelectionWrapEach(sel, fun) => applySelection(doc, sel).flatMap(_.validatedBy(fun))
        case SelectionWrapFlat(sel, fun) => applySelection(doc, sel).flatMap(_.validatedBy(fun).map(_.flatten))
        case Append(wrappers @ _*) => wrappers.map(recurse).combined.map(_.toList)
        case Combine(left, right, fun) => withGood(interpret(left, doc, link), interpret(right, doc, link))(fun)
        case s @ Shuffle(target, fun) => s.run(doc, link)
        case LinkDataDecision(pred, isTrue, isFalse) => if(pred(link.data)) recurse(isTrue) else recurse(isFalse)
        case Focus(selection, continue) => recurse(selection).flatMap{listOfElements =>
          listOfElements.validatedBy(interpret(continue, _, link)).map(_.flatten)}
        case Decision(pred, isTrue, isFalse) => if (pred(doc)) recurse(isTrue) else recurse(isFalse)
        case Constant(t) => t
        case Alternative(left, right) => recurse(left).orElse(recurse(right))
      }

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

  case class SelectionWrapEach[+R](selection: Selection, fun: Element => R Or Every[Report]) extends WrapPart[List[R]]
  case class SelectionWrapFlat[+R](selection: Selection, fun: Element => List[R] Or Every[Report]) extends WrapPart[List[R]]

  case class Append[+T](wrappers: WrapPart[List[T]]*) extends WrapPart[List[T]]
  case class Combine[A, B, +T](left: WrapPart[A], right: WrapPart[B], fun: (A, B) => T) extends WrapPart[T]
  object Combine {
    def of[A, B, T](left: WrapPart[A], right: WrapPart[B])(fun: (A, B) => T): Combine[A, B, T] = Combine(left, right, fun)
  }
  case class Alternative[+T](left: WrapPart[T], right: WrapPart[T]) extends WrapPart[T]


  case class Shuffle[T, +U](target: WrapPart[T], fun: T => U) extends WrapPart[U] {
    def run(doc: Element, link: Link): Or[U, Every[Report]] = interpret(target, doc, link).map(fun)

  }
  object Shuffle {
    def of[T, U](target: WrapPart[T])(fun: T => U): Shuffle[T, U] = Shuffle(target, fun)
  }

  case class Focus[T](selection: WrapPart[List[Element]], continue: WrapPart[List[T]]) extends WrapPart[List[T]]
  case class Decision[T](pred: Element => Boolean, isTrue: WrapPart[T], isFalse: WrapPart[T]) extends WrapPart[T]

  case class Constant[T](c: T Or Every[Report]) extends WrapPart[T]

}

