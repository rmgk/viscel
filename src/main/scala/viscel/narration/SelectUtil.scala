package viscel.narration

import org.jsoup.nodes.Element
import org.scalactic.Accumulation._
import org.scalactic._
import viscel.shared.Story.{Asset, Chapter, More}
import viscel.shared.{AbsUri, Story}

import scala.Predef.ArrowAssoc
import scala.collection.immutable.Set
import scala.language.implicitConversions

object SelectUtil {
	def getAttr(e: Element, k: String): Option[(String, String)] = {
		val res = e.attr(k)
		if (res.isEmpty) None else Some(k -> res)
	}

	def imgIntoAsset(img: Element): Asset Or Every[ErrorMessage] = extract(Asset(
		source = img.attr("abs:src"),
		origin = img.ownerDocument().location(),
		metadata = (getAttr(img, "alt") ++
			getAttr(img, "title") ++
			getAttr(img, "width") ++
			getAttr(img, "height")).toMap))

	def queryImage(query: String)(from: Element): List[Asset] Or Every[ErrorMessage] = Selection(from).unique(query).wrapEach(imgIntoAsset)
	def queryImages(query: String)(from: Element): List[Asset] Or Every[ErrorMessage] = Selection(from).many(query).wrapEach(imgIntoAsset)
	def queryImageInAnchor(query: String, pagetype: String)(from: Element): List[Story] Or Every[ErrorMessage] = Selection(from).unique(query).wrapFlat{ image =>
		imgIntoAsset(image).map(_ :: elementIntoPointer(pagetype)(image.parent()).toOption.toList )
	}

	def extract[R](op: => R): R Or One[ErrorMessage] = attempt(op).badMap(err => s"${ err.getMessage } at ($caller)").accumulating

	/** tries to extract an absolute uri from an element, extraction depends on type of tag */
	def extractUri(element: Element): AbsUri Or One[ErrorMessage] = element.tagName() match {
		case "a" => extract { AbsUri.fromString(element.attr("abs:href")) }
		case "option" => extract { AbsUri.fromString(element.attr("abs:value")) }
		case tag => Bad(One(s"can not extract uri from '$tag' at ($caller): ${ show(element) }"))
	}

	def selectNext(pagetype: String)(elements: List[Element]): List[More] Or Every[ErrorMessage] = elements.validatedBy(elementIntoPointer(pagetype)).flatMap {
		case pointers if elements.isEmpty => Good(Nil)
		case pointers if pointers.toSet.size == 1 => Good(pointers.headOption.toList)
		case pointers => Bad(One(blame("more than one next found", elements: _*)))
	}

	def elementIntoPointer(pagetype: String)(element: Element): More Or Every[ErrorMessage] =
		extractUri(element).map(uri => More(uri, pagetype))

	/** takes an element, extracts its uri and text and generates a description pointing to that chapter */
	def elementIntoChapterPointer(pagetype: String)(chapter: Element): List[Story] Or Every[ErrorMessage] =
		withGood(elementIntoPointer(pagetype)(chapter)) { (pointer) =>
			Chapter(chapter.text()) :: pointer :: Nil
		}

	val ignoredClasses = Set("viscel.narration.Selection", "java.lang.Thread", "viscel.narration.GoodSelection", "org.scalactic", "scala", "viscel.narration.SelectUtil")
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


	def storyFromOr(or: List[Story] Or Every[ErrorMessage]): List[Story] = or.fold(Predef.identity, err => Story.Failed(err.toList) :: Nil)

	def placeChapters(archive: List[Story], chapters: List[(Story, Story)]): List[Story] = (archive, chapters) match {
		case (Nil, chaps) => chaps.flatMap(c => c._1 :: c._2 :: Nil)
		case (as, Nil) => as
		case (a :: as, (c, m) :: cms) if a == m => c :: a :: placeChapters(as, cms)
		case (a :: as, cms) => a :: placeChapters(as, cms)
	}

}
