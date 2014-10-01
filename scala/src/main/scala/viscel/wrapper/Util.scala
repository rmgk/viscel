package viscel.wrapper

import org.jsoup.nodes.Element
import org.scalactic.Accumulation._
import org.scalactic._
import viscel.core._
import viscel.description._

import scala.Predef.any2ArrowAssoc
import scala.collection.immutable.Set
import scala.language.implicitConversions

object Util {
	def getAttr(e: Element, k: String): Option[(String, String)] = {
		val res = e.attr(k)
		if (res.isEmpty) None else Some(k -> res)
	}

	def imgIntoAsset(img: Element): Asset Or Every[ErrorMessage] = extract(Asset(
		source = img.attr("abs:src"),
		origin = img.baseUri,
		metadata = (getAttr(img, "alt") ++
			getAttr(img, "title") ++
			getAttr(img, "width") ++
			getAttr(img, "height")).toMap))

	def queryImage(from: Element, query: String): List[Asset] Or Every[ErrorMessage] = Selection(from).unique(query).wrapEach(imgIntoAsset)
	def queryImages(from: Element, query: String): List[Asset] Or Every[ErrorMessage] = Selection(from).many(query).wrapEach(imgIntoAsset)

	def extract[R](op: => R): R Or One[ErrorMessage] = attempt(op).badMap(err => s"${ err.getMessage } at ($caller)").accumulating

	/** tries to extract an absolute uri from an element, extraction depends on type of tag */
	def extractUri(element: Element): AbsUri Or One[ErrorMessage] = element.tagName() match {
		case "a" => extract { AbsUri.fromString(element.attr("abs:href")) }
		case "option" => extract { AbsUri.fromString(element.attr("abs:value")) }
		case tag => Bad(One(s"can not extract uri from '$tag' at ($caller): ${ show(element) }"))
	}

	def selectNext(pagetype: String)(elements: List[Element]): List[Pointer] Or Every[ErrorMessage] = elements.validatedBy(elementIntoPointer(pagetype)).flatMap {
		case pointers if elements.isEmpty => Good(Nil)
		case pointers if pointers.toSet.size == 1 => Good(pointers.headOption.toList)
		case pointers => Bad(One(blame("more than one next found", elements: _*)))
	}

	def elementIntoPointer(pagetype: String)(element: Element): Pointer Or Every[ErrorMessage] =
		extractUri(element).map(uri => Pointer(uri, pagetype))

	/** takes an element, extracts its uri and text and generates a description pointing to that chapter */
	def elementIntoChapterPointer(pagetype: String)(chapter: Element): List[Description] Or Every[ErrorMessage] =
		withGood(elementIntoPointer(pagetype)(chapter)) { (pointer) =>
			Chapter(chapter.text()) :: pointer :: Nil
		}

	val ignoredClasses = Set("viscel.wrapper.Selection", "java.lang.Thread", "viscel.wrapper.GoodSelection", "org.scalactic", "scala", "viscel.wrapper.Util")
	def caller: String = {
		val stackTraceOption = Predef.wrapRefArray(Thread.currentThread().getStackTrace()).find { ste =>
			val cname = ste.getClassName
			!ignoredClasses.exists(cname.startsWith)
		}
		stackTraceOption.fold("invalid stacktrace") { ste => s"${ ste.getClassName }#${ ste.getMethodName }:${ ste.getLineNumber }" }
	}

	def show(element: Element) = s"${ element.tag }, #${ element.id }, .${ element.classNames }"

	def blame(text: String, cause: Element*): String =
		s"""$text at ($caller) on (${ cause.head.baseUri }) elements (${ cause.map { show } })"""
}
