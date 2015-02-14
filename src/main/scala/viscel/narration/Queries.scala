package viscel.narration

import org.jsoup.nodes.Element
import org.scalactic.Accumulation._
import org.scalactic.TypeCheckedTripleEquals._
import org.scalactic._
import viscel.narration.Data.Chapter
import viscel.scribe.narration.SelectMore._
import viscel.scribe.narration.{Asset, More, Selection, Story}
import viscel.scribe.report.Report
import viscel.scribe.report.ReportTools.{extract, append}

import scala.Predef.{$conforms, ArrowAssoc}
import scala.collection.immutable.Set
import scala.language.implicitConversions
import scala.util.matching.Regex

object Queries {

	object SelectUtilV1 {
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
				else extract { Chapter(elem.text()) }
			}
		}
		def queryChapterArchive(query: String)(from: Element): List[Story] Or Every[Report] = {
			Selection(from).many(query).wrapFlat(elementIntoChapterPointer)
		}

		/** takes an element, extracts its uri and text and generates a description pointing to that chapter */
		def elementIntoChapterPointer(chapter: Element): List[Story] Or Every[Report] =
			extractMore(chapter).map { pointer =>
				Chapter(chapter.text()) :: pointer :: Nil
			}


		def moreData[B](or: List[Story] Or B, data: String): List[Story] Or B = or.map(_.map{
			case More(loc, policy, Nil) => More(loc, policy, data :: Nil)
			case m@More(_, _, _) => throw new IllegalArgumentException(s"tried to add '$data' to $m")
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

}
