package viscel.scribe.narration

import java.net.URL

import org.jsoup.nodes.Element
import org.scalactic.Accumulation._
import org.scalactic._

import scala.Predef.$conforms
import scala.collection.immutable.Set
import scala.language.implicitConversions
import scala.util.matching.Regex

object SelectUtil {
	def getAttr(e: Element, k: String): List[String] = {
		val res = e.attr(k)
		if (res.isEmpty) Nil else List(k, res)
	}

	def imgIntoAsset(img: Element): Asset Or Every[ErrorMessage] = extract(Asset(
		blob = Some(img.attr("abs:src")),
		origin = Some(img.ownerDocument().location()),
		kind = 0,
		data =
			getAttr(img, "alt") :::
			getAttr(img, "title") :::
			getAttr(img, "width") :::
			getAttr(img, "height")))

	def queryImage(query: String)(from: Element): List[Asset] Or Every[ErrorMessage] = Selection(from).unique(query).wrapEach(imgIntoAsset)
	def queryImages(query: String)(from: Element): List[Asset] Or Every[ErrorMessage] = Selection(from).many(query).wrapEach(imgIntoAsset)
	def queryImageInAnchor(query: String)(from: Element): List[Story] Or Every[ErrorMessage] = Selection(from).unique(query).wrapFlat { image =>
		imgIntoAsset(image).map(_ :: elementIntoPointer(image.parent()).toOption.toList)
	}
	def queryNext(query: String)(from: Element): List[More] Or Every[ErrorMessage] = Selection(from).all(query).wrap(selectNext)
	def queryImageNext(imageQuery: String, nextQuery: String)(from: Element): List[Story] Or Every[ErrorMessage] = {
		append(queryImage(imageQuery)(from), queryNext(nextQuery)(from))
	}


	def extract[R](op: => R): R Or One[ErrorMessage] = attempt(op).badMap(err => s"${ err.getMessage } at ($caller)").accumulating

	/** tries to extract an absolute uri from an element, extraction depends on type of tag */
	def extractUri(element: Element): URL Or One[ErrorMessage] = element.tagName() match {
		case "a" => extract { stringToURL(element.attr("abs:href")) }
		case "option" => extract { stringToURL(element.attr("abs:value")) }
		case tag => Bad(One(s"can not extract uri from '$tag' at ($caller): ${ show(element) }"))
	}

	def selectNext(elements: List[Element]): List[More] Or Every[ErrorMessage] = elements.validatedBy(elementIntoPointer).flatMap {
		case pointers if elements.isEmpty => Good(Nil)
		case pointers if pointers.toSet.size == 1 => Good(pointers.headOption.toList)
		case pointers => Bad(One(blame("more than one next found", elements: _*)))
	}

	def elementIntoPointer(element: Element): More Or Every[ErrorMessage] =
		extractUri(element).map(uri => More(uri))


	val ignoredClasses = Set("viscel.scribe", "java", "org.scalactic", "scala")
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

	def append[T](as: Or[List[T], Every[ErrorMessage]]*): Or[List[T], Every[ErrorMessage]] = convertGenTraversableOnceToCombinable(as).combined.map(_.flatten.toList)
	def cons[T](a: T Or Every[ErrorMessage], b: List[T] Or Every[ErrorMessage]): Or[List[T], Every[ErrorMessage]] = withGood(a, b)(_ :: _)


	def groupedOn[T](l: List[T])(p: T => Boolean) = l.foldLeft(List[List[T]]()) {
		case (acc, t) if p(t) => List(t) :: acc
		case (Nil, t) => List(t) :: Nil
		case (a :: as, t) => (t :: a) :: as
	}.map(_.reverse).reverse

	implicit def stringToURL(s: String): URL = new URL(s)

	implicit class RegexContext(val sc: StringContext) {
		object rex {
			def unapplySeq(m: String): Option[Seq[String]] = {
				val regex = new Regex(sc.parts.mkString(""))
				regex.findFirstMatchIn(m).map { gs =>
					gs.subgroups
				}
			}
		}
	}


}
