package viscel.netzi

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.scalactic.Accumulation.{withGood, _}
import org.scalactic.{Every, Good, Or}

object Narration {

  private def applySelection(doc: Element, sel: Selection): List[Element] Or Every[Report] = {
    sel.pipeline.reverse.foldLeft(Good(List(doc)).orBad[Every[Report]]) { (elems, sel) =>
      elems.flatMap { els: List[Element] =>
        val validated: Or[List[List[Element]], Every[Report]] = els.validatedBy(sel)
        validated.map(_.flatten)
      }
    }
  }

  case class Interpreter(cd: ContextData) {
    def interpret[T](outerWrapper: WrapPart[T]): T Or Every[Report] = {
      val document = Jsoup.parse(cd.content, cd.location)
      recurse(outerWrapper)(document)
    }
    def recurse[T](wrapper: WrapPart[T])(implicit element: Element): T Or Every[Report] = {
      val res: Or[T, Every[Report]] = wrapper match {
        case ElementW                               =>
          org.scalactic.attempt(element).badMap(ExtractionFailed.apply).accumulating
        case ContextW                               => Good(cd)
        case Constant(t)                            => Good(t)
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
      }
      res
    }
  }



  sealed trait WrapPart[+T] {
    def map[U](fun: T => U): WrapPart[U] = MapW(this, fun)
  }


  val Volatile = "Volatile"

  def PolicyDecision[T](volatile: WrapPart[T], normal: WrapPart[T]) =
    Condition(ContextW.map(_.context.contains(Volatile)), volatile, normal)

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

  case class ContextData(content: String, context: List[String], location: String)
  // single data element extraction
  case object ContextW extends WrapPart[ContextData]

  // used for vid
  case class AdditionalErrors[+E](target: WrapPart[E], augmentation: Every[Report] => Every[Report])
    extends WrapPart[E]

}
