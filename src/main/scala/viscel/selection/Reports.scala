package viscel.selection

import org.jsoup.nodes.Element
import org.scalactic.Accumulation.{withGood, convertGenTraversableOnceToCombinable => combinable}
import org.scalactic.{Every, One, Or, attempt}
import viscel.selection.ReportTools.show

import scala.collection.immutable.Set
import scala.collection.JavaConverters._

trait Report {
  def describe: String
}

object ReportTools {
  def show(element: Element) = s"${element.tag} ${element.attributes().asScala.map(_.html()).mkString(" ")}"
  def augmentBad[G, B, C](res: G Or Every[B])(aug: B => C): G Or Every[C] = res.badMap(_.map(aug))
  def append[T, E](as: Or[List[T], Every[E]]*): Or[List[T], Every[E]] = combinable(as).combined.map(_.flatten.toList)
  def cons[T, E](a: T Or Every[E], b: List[T] Or Every[E]): Or[List[T], Every[E]] = withGood(a, b)(_ :: _)
  def combine[T, E](as: T Or Every[E]*): Or[List[T], Every[E]] = combinable(as).combined.map(_.toList)
  def extract[R](op: => R): R Or One[ExtractionFailed] = attempt(op).badMap(ExtractionFailed.apply).accumulating
}

trait Stack {
  val stack: List[StackTraceElement] = Predef.wrapRefArray(Thread.currentThread().getStackTrace()).toList
  def position: String = {
    val ignoredClasses = Set("viscel.selection", "java", "org.scalactic", "scala")
    stack.find { ste =>
      val cname = ste.getClassName
      !ignoredClasses.exists(cname.startsWith)
    }.fold("invalid stacktrace") { ste => s"${ste.getClassName}#${ste.getMethodName}:${ste.getLineNumber}" }
  }
}

class FixedReport(override val describe: String) extends Report

case class FailedElement(query: String, reason: Report, element: Element*) extends Report with Stack {
  override def describe: String = s"${reason.describe}: '$query' on <${element map show mkString "; "}> at ($position)"
}

case object QueryNotUnique extends FixedReport("query is not unique")

case object QueryNotMatch extends FixedReport("query did not match")

case object UnhandledTag extends FixedReport("unhandled tag")

case class Fatal(msg: String) extends Report with Stack {
  override def describe: String = s"fatal error '$msg' at $position"
}

case class ExtractionFailed(cause: Throwable) extends Report with Stack {
  override def describe: String = s"extraction failed '${cause.getMessage}' at $position"
}
