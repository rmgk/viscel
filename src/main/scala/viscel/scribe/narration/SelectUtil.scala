package viscel.scribe.narration

import java.net.URL

import org.jsoup.nodes.Element
import org.scalactic.Accumulation._
import org.scalactic._
import viscel.scribe.report.{ExtractionFailed, UnhandledTag, QueryNotUnique, FailedElement, Report}

import scala.Predef.$conforms
import scala.collection.immutable.Set
import scala.language.implicitConversions

object SelectUtil {
	def getAttr(e: Element, k: String): List[String] = {
		val res = e.attr(k)
		if (res.isEmpty) Nil else List(k, res)
	}

	def extract[R](op: => R): R Or One[Report] = attempt(op).badMap(ExtractionFailed.apply).accumulating

	/** tries to extract an absolute uri from an element, extraction depends on type of tag */
	def extractUri(element: Element): URL Or One[Report] = element.tagName() match {
		case "a" => extract { stringToURL(element.attr("abs:href")) }
		case "option" => extract { stringToURL(element.attr("abs:value")) }
		case tag => Bad(One(FailedElement(s"extract uri", UnhandledTag, element)))
	}

	def selectNext(elements: List[Element]): List[More] Or Every[Report] = elements.validatedBy(elementIntoPointer).flatMap {
		case pointers if elements.isEmpty => Good(Nil)
		case pointers if pointers.toSet.size == 1 => Good(pointers.headOption.toList)
		case pointers => Bad(One(FailedElement("next not unique", QueryNotUnique, elements: _*)))
	}

	def elementIntoPointer(element: Element): More Or Every[Report] =
		extractUri(element).map(uri => More(uri))


	val ignoredClasses = Set("viscel.scribe", "java", "org.scalactic", "scala")
	def caller: String = {
		val stackTraceOption = Predef.wrapRefArray(Thread.currentThread().getStackTrace()).find { ste =>
			val cname = ste.getClassName
			!ignoredClasses.exists(cname.startsWith)
		}
		stackTraceOption.fold("invalid stacktrace") { ste => s"${ ste.getClassName }#${ ste.getMethodName }:${ ste.getLineNumber }" }
	}

	implicit def stringToURL(s: String): URL = new URL(s)


}
