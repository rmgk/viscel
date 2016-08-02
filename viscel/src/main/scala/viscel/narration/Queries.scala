package viscel.narration

import java.net.URL

import akka.http.scaladsl.model.Uri
import org.jsoup.nodes.Element
import org.scalactic.Accumulation._
import org.scalactic.TypeCheckedTripleEquals._
import org.scalactic._
import viscel.scribe.{Article, Chapter, Link, Policy, Vuri, WebContent}
import viscel.selection.ReportTools.{extract, _}
import viscel.selection.{FailedElement, QueryNotUnique, Report, Selection, UnhandledTag}

import scala.Predef.{$conforms, ArrowAssoc}
import scala.language.implicitConversions
import scala.util.matching.Regex

object Queries {

	/** tries to extract an absolute uri from an element, extraction depends on type of tag */
	def extractURL(element: Element): Vuri Or One[Report] = element.tagName() match {
		case "a" => extract {Vuri.fromString(element.attr("abs:href"))}
		case "option" => extract {Vuri.fromString(element.attr("abs:value"))}
		case tag => Bad(One(FailedElement(s"extract uri", UnhandledTag, element)))
	}

	def selectMore(elements: List[Element]): List[Link] Or Every[Report] = elements.validatedBy(extractMore).flatMap {
		case pointers if elements.isEmpty => Good(Nil)
		case pointers if pointers.toSet.size == 1 => Good(pointers.headOption.toList)
		case pointers => Bad(One(FailedElement("next not unique", QueryNotUnique, elements: _*)))
	}

	def extractMore(element: Element): Link Or Every[Report] =
		extractURL(element).map(uri => Link(uri))

	def imgIntoAsset(img: Element): Article Or Every[Report] = {
		def getAttr(k: String): Option[(String, String)] = {
			val res = img.attr(k)
			if (res.isEmpty) None else Some(k -> res)
		}
		extract(Article(
			ref = Vuri.fromString(img.attr("abs:src")),
			origin = Vuri.fromString(img.ownerDocument().location()),
			data = List("alt", "title", "width", "height").flatMap(getAttr).toMap))
	}

	def extractChapter(elem: Element): Chapter Or Every[Report] = extract {
		def firstNotEmpty(choices: String*) = choices.find(!_.isEmpty).getOrElse("")
		Chapter(firstNotEmpty(elem.text(), elem.attr("title"), elem.attr("alt")))
	}

	def queryImage(query: String)(from: Element): List[Article] Or Every[Report] = Selection(from).unique(query).wrapEach(imgIntoAsset)
	def queryImages(query: String)(from: Element): List[Article] Or Every[Report] = Selection(from).many(query).wrapEach(imgIntoAsset)
	def queryImageInAnchor(query: String)(from: Element): List[WebContent] Or Every[Report] = Selection(from).unique(query).wrapFlat { image =>
		imgIntoAsset(image).map(_ :: extractMore(image.parent()).toOption.toList)
	}
	def queryNext(query: String)(from: Element): List[Link] Or Every[Report] = Selection(from).all(query).wrap(selectMore)
	def queryImageNext(imageQuery: String, nextQuery: String)(from: Element): List[WebContent] Or Every[Report] = {
		append(queryImage(imageQuery)(from), queryNext(nextQuery)(from))
	}
	def queryMixedArchive(query: String)(from: Element): List[WebContent] Or Every[Report] = {
		Selection(from).many(query).wrapEach { elem =>
			if (elem.tagName() === "a") extractMore(elem)
			else extractChapter(elem)
		}
	}
	def queryChapterArchive(query: String)(from: Element): List[WebContent] Or Every[Report] = {
		Selection(from).many(query).wrapFlat(elementIntoChapterPointer)
	}

	/** takes an element, extracts its uri and text and generates a description pointing to that chapter */
	def elementIntoChapterPointer(chapter: Element): List[WebContent] Or Every[Report] =
		combine(extractChapter(chapter), extractMore(chapter))


	def moreData[B](or: List[WebContent] Or B, data: String): List[WebContent] Or B = or.map(_.map {
		case Link(loc, policy, Nil) => Link(loc, policy, data :: Nil)
		case m@Link(_, _, _) => throw new IllegalArgumentException(s"tried to add '$data' to $m")
		case o => o
	})

	def morePolicy[B](policy: Policy, or: List[WebContent] Or B): List[WebContent] Or B = or.map(_.map {
		case Link(loc, _, data) => Link(loc, policy, data)
		case o => o
	})


	def placeChapters(archive: List[WebContent], chapters: List[(WebContent, WebContent)]): List[WebContent] = (archive, chapters) match {
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

	def reverse(stories: List[WebContent]): List[WebContent] =
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
