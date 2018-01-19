package viscel.narration

import org.jsoup.helper.StringUtil
import org.jsoup.nodes.Element
import org.scalactic.Accumulation._
import org.scalactic.TypeCheckedTripleEquals._
import org.scalactic._
import viscel.scribe.{ArticleRef, Chapter, Link, Policy, Vurl, WebContent}
import viscel.selection.ReportTools.{extract, _}
import viscel.selection.{FailedElement, QueryNotUnique, Report, Selection, UnhandledTag}

import scala.util.matching.Regex

object Queries {

	/** tries to extract an absolute uri from an element, extraction depends on type of tag */
	def extractURL(element: Element): Vurl Or One[Report] = element.tagName() match {
		case "a" => extract {Vurl.fromString(element.attr("abs:href"))}
		case "link" => extract {Vurl.fromString(element.attr("abs:href"))}
		case "option" => extract {Vurl.fromString(element.attr("abs:value"))}
		case _ => Bad(One(FailedElement(s"extract uri", UnhandledTag, element)))
	}

	def selectMore(elements: List[Element]): List[Link] Or Every[Report] = elements.validatedBy(extractMore).flatMap {
		case pointers if elements.isEmpty => Good(Nil)
		case pointers if pointers.toSet.size == 1 => Good(pointers.headOption.toList)
		case pointers => Bad(One(FailedElement("next not unique", QueryNotUnique, elements: _*)))
	}

	def extractMore(element: Element): Link Or Every[Report] =
		extractURL(element).map(uri => Link(uri))

	def intoArticle(img: Element): ArticleRef Or Every[Report] = {
		def getAttr(k: String): Option[(String, String)] = {
			val res = img.attr(k)
			if (res.isEmpty) None else Some(k -> res)
		}
		img.tagName() match {
			case "img" =>
				extract(ArticleRef(
					ref = Vurl.fromString(img.attr("abs:src")),
					origin = Vurl.fromString(img.ownerDocument().location()),
					data = List("alt", "title", "width", "height").flatMap(getAttr).toMap))
			case "embed" =>
				extract(ArticleRef(
					ref = Vurl.fromString(img.attr("abs:src")),
					origin = Vurl.fromString(img.ownerDocument().location()),
					data = List("width", "height", "type").flatMap(getAttr).toMap))
			case "object" =>
				extract(ArticleRef(
					ref = Vurl.fromString(img.attr("abs:data")),
					origin = Vurl.fromString(img.ownerDocument().location()),
					data = List("width", "height", "type").flatMap(getAttr).toMap))
			case _ =>
				val extractBG = """.*background\-image\:url\(([^)]+)\).*""".r("url")
				img.attr("style") match {
					case extractBG(url) =>
						extract(ArticleRef(
							ref = Vurl.fromString(StringUtil.resolve(img.ownerDocument().location(), url)),
							origin = Vurl.fromString(img.ownerDocument().location())))
					case _ => Bad(One(FailedElement(s"into article", UnhandledTag, img)))
				}
		}

	}

	def extractChapter(elem: Element): Chapter Or Every[Report] = extract {
		def firstNotEmpty(choices: String*) = choices.find(!_.isEmpty).getOrElse("")
		Chapter(firstNotEmpty(elem.text(), elem.attr("title"), elem.attr("alt")))
	}

	def queryImage(query: String)(from: Element): List[ArticleRef] Or Every[Report] = Selection(from).unique(query).wrapEach(intoArticle)
	def queryImages(query: String)(from: Element): List[ArticleRef] Or Every[Report] = Selection(from).many(query).wrapEach(intoArticle)
	/** extracts artical at query result
	  * optionally extracts direct parent of query result as link */
	def queryImageInAnchor(query: String)(from: Element): Contents = Selection(from).unique(query).wrapFlat { image =>
		intoArticle(image).map(_ :: extractMore(image.parent()).toOption.toList)
	}
	def queryNext(query: String)(from: Element): List[Link] Or Every[Report] = Selection(from).all(query).wrap(selectMore)
	def queryImageNext(imageQuery: String, nextQuery: String)(from: Element): Contents = {
		append(queryImage(imageQuery)(from), queryNext(nextQuery)(from))
	}
	def queryMixedArchive(query: String)(from: Element): Contents = {
		Selection(from).many(query).wrapEach { elem =>
			if (elem.tagName() === "a") extractMore(elem)
			else extractChapter(elem)
		}
	}
	def queryChapterArchive(query: String)(from: Element): Contents = {
		Selection(from).many(query).wrapFlat(elementIntoChapterPointer)
	}

	/** takes an element, extracts its uri and text and generates a description pointing to that chapter */
	def elementIntoChapterPointer(chapter: Element): Contents =
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

	def groupedOn[T](l: List[T])(p: T => Boolean): List[List[T]] = l.foldLeft(List[List[T]]()) {
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
