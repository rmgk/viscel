package viscel.narration

import java.net.URL

import org.jsoup.nodes.Element
import org.scalactic.Accumulation._
import org.scalactic.TypeCheckedTripleEquals._
import org.scalactic._
import viscel.scribe.narration.{Article, Chapter, Link, Policy, PageContent}
import viscel.selection.ReportTools.{extract, _}
import viscel.selection.{FailedElement, QueryNotUnique, Report, Selection, UnhandledTag}

import scala.Predef.{$conforms, ArrowAssoc}
import scala.language.implicitConversions
import scala.util.matching.Regex

object Queries {

	/** tries to extract an absolute uri from an element, extraction depends on type of tag */
	def extractURL(element: Element): URL Or One[Report] = element.tagName() match {
		case "a" => extract {stringToURL(element.attr("abs:href"))}
		case "option" => extract {stringToURL(element.attr("abs:value"))}
		case tag => Bad(One(FailedElement(s"extract uri", UnhandledTag, element)))
	}

	def selectMore(elements: List[Element]): List[Link] Or Every[Report] = elements.validatedBy(extractMore).flatMap {
		case pointers if elements.isEmpty => Good(Nil)
		case pointers if pointers.toSet.size == 1 => Good(pointers.headOption.toList)
		case pointers => Bad(One(FailedElement("next not unique", QueryNotUnique, elements: _*)))
	}

	def extractMore(element: Element): Link Or Every[Report] =
		extractURL(element).map(uri => Link(uri))

	implicit def stringToURL(s: String): URL = new URL(s)

	def imgIntoAsset(img: Element): Article Or Every[Report] = {
		def getAttr(k: String): Option[(String, String)] = {
			val res = img.attr(k)
			if (res.isEmpty) None else Some(k -> res)
		}
		extract(Article(
			ref = img.attr("abs:src"),
			origin = img.ownerDocument().location(),
			data = List("alt", "title", "width", "height").flatMap(getAttr).toMap))
	}

	def extractChapter(elem: Element): Chapter Or Every[Report] = extract {
		def firstNotEmpty(choices: String*) = choices.find(!_.isEmpty).getOrElse("")
		Chapter(firstNotEmpty(elem.text(), elem.attr("title"), elem.attr("alt")))
	}

	def queryImage(query: String)(from: Element): List[Article] Or Every[Report] = Selection(from).unique(query).wrapEach(imgIntoAsset)
	def queryImages(query: String)(from: Element): List[Article] Or Every[Report] = Selection(from).many(query).wrapEach(imgIntoAsset)
	def queryImageInAnchor(query: String)(from: Element): List[PageContent] Or Every[Report] = Selection(from).unique(query).wrapFlat { image =>
		imgIntoAsset(image).map(_ :: extractMore(image.parent()).toOption.toList)
	}
	def queryNext(query: String)(from: Element): List[Link] Or Every[Report] = Selection(from).all(query).wrap(selectMore)
	def queryImageNext(imageQuery: String, nextQuery: String)(from: Element): List[PageContent] Or Every[Report] = {
		append(queryImage(imageQuery)(from), queryNext(nextQuery)(from))
	}
	def queryMixedArchive(query: String)(from: Element): List[PageContent] Or Every[Report] = {
		Selection(from).many(query).wrapEach { elem =>
			if (elem.tagName() === "a") extractMore(elem)
			else extractChapter(elem)
		}
	}
	def queryChapterArchive(query: String)(from: Element): List[PageContent] Or Every[Report] = {
		Selection(from).many(query).wrapFlat(elementIntoChapterPointer)
	}

	/** takes an element, extracts its uri and text and generates a description pointing to that chapter */
	def elementIntoChapterPointer(chapter: Element): List[PageContent] Or Every[Report] =
		combine(extractChapter(chapter), extractMore(chapter))


	def moreData[B](or: List[PageContent] Or B, data: String): List[PageContent] Or B = or.map(_.map {
		case Link(loc, policy, Nil) => Link(loc, policy, data :: Nil)
		case m@Link(_, _, _) => throw new IllegalArgumentException(s"tried to add '$data' to $m")
		case o => o
	})

	def morePolicy[B](policy: Policy, or: List[PageContent] Or B): List[PageContent] Or B = or.map(_.map {
		case Link(loc, _, data) => Link(loc, policy, data)
		case o => o
	})


	def placeChapters(archive: List[PageContent], chapters: List[(PageContent, PageContent)]): List[PageContent] = (archive, chapters) match {
		case (Nil, chaps) => chaps.flatMap(c => c._1 :: c._2 :: Nil)
		case (as, Nil) => as
		case (a :: as, (c, m) :: cms) if a == m => c :: a :: placeChapters(as, cms)
		case (a :: as, cms) => a :: placeChapters(as, cms)
	}

	def groupedOn[T](l: List[T])(p: T => Boolean) = l.foldLeft(List[List[T]]()) {
		case (acc, t) if p(t) => List(t) :: acc
		case (Nil, t) => List(t) :: Nil
		case (a :: as, t) => (t :: a) :: as
	}.map(_.reverse).reverse

	def reverse(stories: List[PageContent]): List[PageContent] =
		groupedOn(stories) { case Chapter(_) => true; case _ => false }.reverse.flatMap {
			case (h :: t) => h :: t.reverse
			case Nil => Nil
		}

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
