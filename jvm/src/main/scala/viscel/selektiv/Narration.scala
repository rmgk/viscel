package viscel.selektiv

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import viscel.netzi.{VRequest, VResponse}

object Narration {

  case class Interpreter(cd: ContextData) {
    def interpret[T](outerWrapper: WrapPart[T]): T = {
      val document = Jsoup.parse(cd.response.content, cd.response.location.uriString())
      recurse(outerWrapper)(document)
    }
    def recurse[T](wrapper: WrapPart[T])(implicit element: Element): T = {
      wrapper match {
        case ElementW        => element
        case ContextW        => cd
        case Constant(value) => value
        case Condition(pred, isTrue, isFalse) =>
          val c = recurse(pred)
          if (c) recurse(isTrue) else recurse(isFalse)
        case AdditionalErrors(target, augmentation) =>
          try { recurse(target) }
          catch { case r: Report => throw augmentation(r) }
        case Combination(left, right, fun) => fun(recurse(left), recurse(right))
      }
    }
  }

  sealed trait WrapPart[+T] {
    def map[U](fun: T => U): WrapPart[U] = Combination.of(this, Constant(()))((a, _) => fun(a))
  }
  object WrapPart {
    // this is not very safe, but good enough for now
    given [A, B]: CanEqual[WrapPart[A], WrapPart[B]] = CanEqual.canEqualAny
  }

  case class Constant[T](value: T) extends WrapPart[T]

  case class Condition[T](pred: WrapPart[Boolean], isTrue: WrapPart[T], isFalse: WrapPart[T]) extends WrapPart[T]

  case object ElementW extends WrapPart[Element]

  def Append[T](left: WrapPart[List[T]], right: WrapPart[List[T]]): WrapPart[List[T]] =
    Combination.of(left, right) { (l, r) => l ::: r }

  case class Combination[A, B, +T] private (left: WrapPart[A], right: WrapPart[B], fun: (A, B) => T) extends WrapPart[T]

  object Combination {
    def of[A, B, T](left: WrapPart[A], right: WrapPart[B])(fun: (A, B) => T): Combination[A, B, T] =
      Combination(left, right, fun)
  }

  case class ContextData(request: VRequest, response: VResponse[String]) {
    def content  = response.content
    def location = response.location.uriString()
  }
  // single data element extraction
  case object ContextW extends WrapPart[ContextData]

  // used for vid
  case class AdditionalErrors[+E](target: WrapPart[E], augmentation: Report => Report) extends WrapPart[E]

}
