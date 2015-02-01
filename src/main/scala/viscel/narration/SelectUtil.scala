package viscel.narration

import java.net.URL

import org.jsoup.nodes.Element
import org.scalactic.Accumulation._
import org.scalactic._
import org.scalactic.TypeCheckedTripleEquals._
import viscel.shared.Story.More.Kind
import viscel.shared.Story.{Asset, Chapter, More}
import viscel.shared.{Story, ViscelUrl}

import scala.Predef.{$conforms, ArrowAssoc}
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
	def queryImageInAnchor(query: String, pagetype: Kind)(from: Element): List[Story] Or Every[ErrorMessage] = Selection(from).unique(query).wrapFlat { image =>
		imgIntoAsset(image).map(_ :: elementIntoPointer(pagetype)(image.parent()).toOption.toList)
	}
	def queryNext(query: String, pagetype: Kind)(from: Element): List[More] Or Every[ErrorMessage] = Selection(from).all(query).wrap(selectNext(pagetype))
	def queryImageNext(imageQuery: String, nextQuery: String, pagetype: Kind)(from: Element): List[Story] Or Every[ErrorMessage] = {
		append(queryImage(imageQuery)(from), queryNext(nextQuery, pagetype)(from))
	}
	def queryMixedArchive(query: String, pagetype: Kind)(from: Element):  List[Story] Or Every[ErrorMessage] = {
		Selection(from).many(query).wrapEach { elem =>
			if (elem.tagName() === "a") elementIntoPointer(pagetype)(elem)
			else extract { Chapter(elem.text()) }
		}
	}
	def queryChapterArchive(query: String, pagetye: Kind)(from: Element):  List[Story] Or Every[ErrorMessage] = {
		Selection(from).many(query).wrapFlat(elementIntoChapterPointer(pagetye))
	}


	def extract[R](op: => R): R Or One[ErrorMessage] = attempt(op).badMap(err => s"${ err.getMessage } at ($caller)").accumulating

	/** tries to extract an absolute uri from an element, extraction depends on type of tag */
	def extractUri(element: Element): ViscelUrl Or One[ErrorMessage] = element.tagName() match {
		case "a" => extract { stringToVurl(element.attr("abs:href")) }
		case "option" => extract { stringToVurl(element.attr("abs:value")) }
		case tag => Bad(One(s"can not extract uri from '$tag' at ($caller): ${ show(element) }"))
	}

	def selectNext(pagetype: Kind)(elements: List[Element]): List[More] Or Every[ErrorMessage] = elements.validatedBy(elementIntoPointer(pagetype)).flatMap {
		case pointers if elements.isEmpty => Good(Nil)
		case pointers if pointers.toSet.size == 1 => Good(pointers.headOption.toList)
		case pointers => Bad(One(blame("more than one next found", elements: _*)))
	}

	def elementIntoPointer(pagetype: Kind)(element: Element): More Or Every[ErrorMessage] =
		extractUri(element).map(uri => More(uri, pagetype))

	/** takes an element, extracts its uri and text and generates a description pointing to that chapter */
	def elementIntoChapterPointer(pagetype: Kind)(chapter: Element): List[Story] Or Every[ErrorMessage] =
		elementIntoPointer(pagetype)(chapter).map { pointer =>
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

	def append[T](as: Or[List[T], Every[ErrorMessage]]*): Or[List[T], Every[ErrorMessage]] = convertGenTraversableOnceToCombinable(as).combined.map(_.flatten.toList)
	def cons[T](a: T Or Every[ErrorMessage], b: List[T] Or Every[ErrorMessage]): Or[List[T], Every[ErrorMessage]] = withGood(a, b)(_ :: _)


	implicit def vurlToString(vurl: ViscelUrl): String = vurl.self
	implicit def stringToVurl(url: String): ViscelUrl = new ViscelUrl(new URL(url).toString)

	def groupedOn[T](l: List[T])(p: T => Boolean) = l.foldLeft(List[List[T]]()) {
		case (acc, t) if p(t) => List(t) :: acc
		case (Nil, t) => List(t) :: Nil
		case (a :: as, t) => (t :: a) :: as
	}.map(_.reverse).reverse

}
