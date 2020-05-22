package viscel.selektiv

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object Narration {


  case class Interpreter(cd: ContextData) {
    def interpret[T](outerWrapper: WrapPart[T]): T = {
      val document = Jsoup.parse(cd.content, cd.location)
      recurse(outerWrapper)(document)
    }
    def recurse[T](wrapper: WrapPart[T])(implicit element: Element): T = {
      wrapper match {
        case ElementW                               => element
        case ContextW                               => cd
        case Constant(t)                            => t
        case Condition(pred, isTrue, isFalse)       =>
          val c = recurse(pred)
          if (c) recurse(isTrue) else recurse(isFalse)
        case AdditionalErrors(target, augmentation) =>
          try {recurse(target)}
          catch {case r: Report => throw augmentation(r)}
        case Combination(left, right, fun)          => fun(recurse(left), recurse(right))
        case Focus(selection, continue)             =>
          val loe = recurse(selection)
          loe.flatMap(recurse(continue)(_))
      }
    }
  }


  sealed trait WrapPart[+T] {
    def map[U](fun: T => U): WrapPart[U] = Combination.of(this, Constant(()))((a, _) => fun(a))
  }


  val Volatile = "Volatile"

  def PolicyDecision[T](volatile: WrapPart[T], normal: WrapPart[T]) =
    Condition(ContextW.map(_.context.contains(Volatile)), volatile, normal)

  case class Condition[T](pred: WrapPart[Boolean], isTrue: WrapPart[T], isFalse: WrapPart[T])
    extends WrapPart[T]


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

  case class Focus[T](selection: WrapPart[List[Element]], continue: WrapPart[List[T]])
    extends WrapPart[List[T]]

  case class Constant[T](c: T) extends WrapPart[T]

  case class ContextData(content: String, context: List[String], location: String)
  // single data element extraction
  case object ContextW extends WrapPart[ContextData]

  // used for vid
  case class AdditionalErrors[+E](target: WrapPart[E], augmentation: Report => Report)
    extends WrapPart[E]

}
