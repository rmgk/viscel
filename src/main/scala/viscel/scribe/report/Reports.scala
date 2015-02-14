package viscel.scribe.report

import org.jsoup.nodes.Element
import org.scalactic.Accumulation.{convertGenTraversableOnceToCombinable, withGood}
import org.scalactic.{Every, Or}
import viscel.scribe.report.ReportTools.show

import scala.Predef.$conforms
import scala.collection.immutable.Set

trait Report {
	def describe: String
}

object ReportTools {
	def show(element: Element) = s"${ element.tag }, #${ element.id }, .${ element.classNames }"
	def augmentBad[G, B, C](res: G Or Every[B])(aug: B => C): G Or Every[C] = res.badMap(_.map(aug))
	def append[T, E](as: Or[List[T], Every[E]]*): Or[List[T], Every[E]] = convertGenTraversableOnceToCombinable(as).combined.map(_.flatten.toList)
	def cons[T, E](a: T Or Every[E], b: List[T] Or Every[E]): Or[List[T], Every[E]] = withGood(a, b)(_ :: _)
}

trait Stack {
	val stack: List[StackTraceElement] = Predef.wrapRefArray(Thread.currentThread().getStackTrace()).toList
	def position = {
		val ignoredClasses = Set("viscel.scribe", "java", "org.scalactic", "scala")
		stack.find { ste =>
			val cname = ste.getClassName
			!ignoredClasses.exists(cname.startsWith)
		}.fold("invalid stacktrace") { ste => s"${ ste.getClassName }#${ ste.getMethodName }:${ ste.getLineNumber }" }
	}
}

class FixedReport(override val describe: String) extends Report

case class TextReport(override val describe: String) extends Report

case class Precondition(override val describe: String) extends Report

case class FailedElement(query: String, reason: Report, element: Element*) extends Report with Stack {
	override def describe: String = s"failed '$query' at ($position) because $reason on <${element map show}>"
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