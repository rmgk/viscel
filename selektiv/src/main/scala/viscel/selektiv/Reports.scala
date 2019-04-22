package viscel.selektiv

import org.jsoup.nodes.Element

import scala.collection.JavaConverters._
import scala.collection.immutable.Set
import scala.util.control.NonFatal

trait Report extends RuntimeException {
  def describe: String
}

object ReportTools {
  def show(element: Element) = s"${element.tag} ${element.attributes().asScala.map(_.html()).mkString(" ")}"
  def extract[R](op: => R): R = try op catch {case NonFatal(e) => throw ExtractionFailed(e) }
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
  override def describe: String =
    s"${reason.describe}: '$query' on <${element map ReportTools.show mkString "; "}> at ($position)"
}

case object QueryNotUnique extends FixedReport("query is not unique")

case object QueryNotMatch extends FixedReport("query did not match")

case object UnhandledTag extends FixedReport("unhandled tag")

case class ExtractionFailed(cause: Throwable) extends Report with Stack {
  override def describe: String = s"extraction failed '${cause.getMessage}' at $position"
}
