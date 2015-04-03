package viscel.narration

import org.jsoup.nodes.Element
import org.scalactic.TypeCheckedTripleEquals._
import org.scalactic._
import viscel.narration.Data.Chapter
import viscel.narration.SelectMore._
import viscel.scribe.narration.{Asset, More, Policy, Story}
import viscel.selection.ReportTools._
import viscel.selection.{Report, Selection}

import scala.Predef.{$conforms, ArrowAssoc}
import scala.language.implicitConversions
import scala.util.matching.Regex

object Queries {

	def getAttr(e: Element, k: String): Option[(String, String)] = {
		val res = e.attr(k)
		if (res.isEmpty) None else Some(k -> res)
	}

	def imgIntoAsset(img: Element): Asset Or Every[Report] = extract(Data.Article(
		blob = img.attr("abs:src"),
		origin = img.ownerDocument().location(),
		data = (getAttr(img, "alt") ++
			getAttr(img, "title") ++
			getAttr(img, "width") ++
			getAttr(img, "height")).toMap))

	def extractChapter(elem: Element): Asset Or Every[Report] = extract {
		def firstNotEmpty(choices: String*) = choices.find(!_.isEmpty).getOrElse("")
		Chapter(firstNotEmpty(elem.text(), elem.attr("title"), elem.attr("alt")))
	}

	def queryImage(query: String)(from: Element): List[Asset] Or Every[Report] = Selection(from).unique(query).wrapEach(imgIntoAsset)
	def queryImages(query: String)(from: Element): List[Asset] Or Every[Report] = Selection(from).many(query).wrapEach(imgIntoAsset)
	def queryImageInAnchor(query: String)(from: Element): List[Story] Or Every[Report] = Selection(from).unique(query).wrapFlat { image =>
		imgIntoAsset(image).map(_ :: extractMore(image.parent()).toOption.toList)
	}
	def queryNext(query: String)(from: Element): List[More] Or Every[Report] = Selection(from).all(query).wrap(selectMore)
	def queryImageNext(imageQuery: String, nextQuery: String)(from: Element): List[Story] Or Every[Report] = {
		append(queryImage(imageQuery)(from), queryNext(nextQuery)(from))
	}
	def queryMixedArchive(query: String)(from: Element): List[Story] Or Every[Report] = {
		Selection(from).many(query).wrapEach { elem =>
			if (elem.tagName() === "a") extractMore(elem)
			else extractChapter(elem)
		}
	}
	def queryChapterArchive(query: String)(from: Element): List[Story] Or Every[Report] = {
		Selection(from).many(query).wrapFlat(elementIntoChapterPointer)
	}

	/** takes an element, extracts its uri and text and generates a description pointing to that chapter */
	def elementIntoChapterPointer(chapter: Element): List[Story] Or Every[Report] =
		combine(extractChapter(chapter), extractMore(chapter))


	def moreData[B](or: List[Story] Or B, data: String): List[Story] Or B = or.map(_.map {
		case More(loc, policy, Nil) => More(loc, policy, data :: Nil)
		case m@More(_, _, _) => throw new IllegalArgumentException(s"tried to add '$data' to $m")
		case o => o
	})

	def morePolicy[B](policy: Policy, or: List[Story] Or B): List[Story] Or B = or.map(_.map {
		case More(loc, _, data) => More(loc, policy, data)
		case o => o
	})


	def placeChapters(archive: List[Story], chapters: List[(Story, Story)]): List[Story] = (archive, chapters) match {
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

	def reverse(stories: List[Story]): List[Story] =
		groupedOn(stories) { case Asset(_, _, AssetKind.chapter, _) => true; case _ => false }.reverse.flatMap {
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
