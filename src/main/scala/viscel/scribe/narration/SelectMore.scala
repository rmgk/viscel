package viscel.scribe.narration

import java.net.URL

import org.jsoup.nodes.Element
import org.scalactic.Accumulation._
import org.scalactic._
import viscel.scribe.report.ReportTools.extract
import viscel.scribe.report.{FailedElement, QueryNotUnique, Report, UnhandledTag}

import scala.language.implicitConversions

object SelectMore {

	/** tries to extract an absolute uri from an element, extraction depends on type of tag */
	def extractURL(element: Element): URL Or One[Report] = element.tagName() match {
		case "a" => extract { stringToURL(element.attr("abs:href")) }
		case "option" => extract { stringToURL(element.attr("abs:value")) }
		case tag => Bad(One(FailedElement(s"extract uri", UnhandledTag, element)))
	}

	def selectMore(elements: List[Element]): List[More] Or Every[Report] = elements.validatedBy(extractMore).flatMap {
		case pointers if elements.isEmpty => Good(Nil)
		case pointers if pointers.toSet.size == 1 => Good(pointers.headOption.toList)
		case pointers => Bad(One(FailedElement("next not unique", QueryNotUnique, elements: _*)))
	}

	def extractMore(element: Element): More Or Every[Report] =
		extractURL(element).map(uri => More(uri))

	implicit def stringToURL(s: String): URL = new URL(s)

}
