package viscel.wrapper

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.jsoup.nodes.Element
import org.scalactic.Accumulation._
import org.scalactic._
import viscel.core._
import viscel.description._

import scala.collection.JavaConversions._
import scala.language.implicitConversions

object Util {
	def getAttr(e: Element, k: String): Option[(String, String)] = {
		val res = e.attr(k)
		if (res.isEmpty) None else Some(k -> res)
	}

	def imgToElement(img: Element): ElementContent Or Every[ErrorMessage] = extract(ElementContent(
		source = img.attr("abs:src"),
		origin = img.baseUri,
		props = (getAttr(img, "alt") ++
			getAttr(img, "title") ++
			getAttr(img, "width") ++
			getAttr(img, "height")).toMap))

	def imgIntoStructure(img: Element) = imgToElement(img).map{Structure(_)}

	def extract[R](op: => R): R Or One[ErrorMessage] = attempt(op).badMap(_.getMessage()).accumulating

	def extractUri(element: Element): AbsUri Or One[ErrorMessage] = element.tagName() match {
		case "a" => extract { AbsUri.fromString(element.attr("abs:href")) }
		case _ => Bad(One(s"not an anchor at ($caller): ${show(element)}"))
	}

	def selectNext(pagetype: String)(elements: Seq[Element]): Description Or Every[ErrorMessage] = elements.validatedBy(anchorIntoPointer(pagetype)).flatMap {
		case pointers if elements.isEmpty => Good(EmptyDescription)
		case pointers if pointers.toSet.size == 1 => Good(pointers.head)
		case pointers => Bad(One(blame("more than one next found", elements: _*)))
	}

	def anchorIntoPointer(pagetype: String)(element: Element): Pointer Or Every[ErrorMessage] =
		extractUri(element).map(uri => Pointer(uri, pagetype))

	val ignoredClasses = Set("viscel.wrapper.Selection", "java.lang.Thread", "viscel.wrapper.GoodSelection", "org.scalactic", "scala", "viscel.wrapper.Util")
	def caller: String = {
		val stackTraceOption = Thread.currentThread().getStackTrace().find { ste =>
			val cname = ste.getClassName
			!ignoredClasses.exists(cname.startsWith)
		}
		stackTraceOption.fold("invalid stacktrace"){ste => s"${ ste.getClassName }#${ ste.getMethodName }:${ ste.getLineNumber }" }
	}

	def show(element: Element) = s"${ element.tag }, #${ element.id }, .${ element.classNames }"

	def blame(text: String, cause: Element*): String =
		s"""$text at ($caller) on (${cause.head.baseUri}) elements (${cause.map{show}})"""
}