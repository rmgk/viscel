package viscel.narration

import org.jsoup.helper.StringUtil
import org.jsoup.nodes.Element
import org.scalactic.Accumulation._
import org.scalactic.TypeCheckedTripleEquals._
import org.scalactic._
import viscel.narration.interpretation.NarrationInterpretation.{Append, WrapPart, Wrapper}
import viscel.selection.ReportTools._
import viscel.selection.{FailedElement, QueryNotUnique, Report, Selection, UnhandledTag}
import viscel.store.Vurl
import viscel.store.v4.DataRow

import scala.util.matching.Regex

object Queries {

  /** tries to extract an absolute uri from an element, extraction depends on type of tag */
  def extractURL(element: Element): Vurl Or One[Report] = element.tagName() match {
    case "a" => extract {Vurl.fromString(element.attr("abs:href"))}
    case "link" => extract {Vurl.fromString(element.attr("abs:href"))}
    case "option" => extract {Vurl.fromString(element.attr("abs:value"))}
    case _ => Bad(One(FailedElement(s"extract uri", UnhandledTag, element)))
  }

  def selectMore(elements: List[Element]): List[DataRow.Link] Or Every[Report] =
    if (elements.isEmpty) Good(Nil)
    else elements.validatedBy(extractMore).flatMap {
      case pointers if pointers.toSet.size == 1 => Good(pointers.headOption.toList)
      case pointers => Bad(One(FailedElement("next not unique", QueryNotUnique, elements: _*)))
    }

  def extractMore(element: Element): DataRow.Link Or Every[Report] =
    extractURL(element).map(uri => DataRow.Link(uri))

  def intoArticle(img: Element): DataRow.Link Or Every[Report] = {
    imageFromAttribute(img, None)
  }

  def imageFromAttribute(img: Element, customAttr: Option[String]): DataRow.Link Or Every[Report]  = {
    def getAttr(k: String): List[String] = {
      val res = img.attr(k)
      if (res.isEmpty) Nil else List(k, res)
    }

    img.tagName() match {
      case "img"    =>
        extract(DataRow.Link(
          ref = Vurl.fromString(img.attr(customAttr.getOrElse("abs:src"))),
          data = List("alt", "title", "width", "height").flatMap(getAttr)))
      case "embed"  =>
        extract(DataRow.Link(
          ref = Vurl.fromString(img.attr(customAttr.getOrElse("abs:src"))),
          data = List("width", "height", "type").flatMap(getAttr)))
      case "object" =>
        extract(DataRow.Link(
          ref = Vurl.fromString(img.attr(customAttr.getOrElse("abs:data"))),
          data = List("width", "height", "type").flatMap(getAttr)))
      case _        =>
        val extractBG = """.*background\-image\:url\(([^)]+)\).*""".r("url")
        img.attr("style") match {
          case extractBG(url) =>
            extract(DataRow.Link(
              ref = Vurl.fromString(StringUtil.resolve(img.ownerDocument().location(), url))))
          case _              => Bad(One(FailedElement(s"into article", UnhandledTag, img)))
        }
    }
  }
  def extractChapter(elem: Element): DataRow.Chapter Or Every[Report] = extract {
    def firstNotEmpty(choices: String*) = choices.find(!_.isBlank).getOrElse("")

    DataRow.Chapter(firstNotEmpty(elem.text(), elem.attr("title"), elem.attr("alt")))
  }

  def queryImage(query: String): WrapPart[List[DataRow.Link]] = Selection.unique(query).wrapEach(intoArticle)
  def queryImages(query: String): WrapPart[List[DataRow.Link]] = Selection.many(query).wrapEach(intoArticle)
  /** extracts article at query result
    * optionally extracts direct parent of query result as link */
  def queryImageInAnchor(query: String): Wrapper = Selection.unique(query).wrapFlat[DataRow.Content] { image =>
    intoArticle(image).map { art: DataRow.Link =>
      val wc: List[DataRow.Content] = extractMore(image.parent()).toOption.toList
      art ::  wc }
  }
  def queryNext(query: String): WrapPart[List[DataRow.Link]] = Selection.all(query).wrap(selectMore)
  def queryImageNext(imageQuery: String, nextQuery: String): Wrapper = {
    Append(queryImage(imageQuery), queryNext(nextQuery))
  }
  def queryMixedArchive(query: String): Wrapper = {
    def intoMixedArchive(elem: Element): DataRow.Content Or Every[Report] = {
      if (elem.tagName() === "a") extractMore(elem)
      else extractChapter(elem)
    }

    Selection.many(query).wrapEach {intoMixedArchive}
  }

  def queryChapterArchive(query: String): Wrapper = {
    /* takes an element, extracts its uri and text and generates a description pointing to that chapter */
    def elementIntoChapterPointer(chapter: Element): List[DataRow.Content] Or Every[Report] =
      combine(extractChapter(chapter), extractMore(chapter))

    Selection.many(query).wrapFlat(elementIntoChapterPointer)
  }


  def chapterReverse(stories: List[DataRow.Content]): List[DataRow.Content] = {
    def groupedOn[T](l: List[T])(p: T => Boolean): List[List[T]] = l.foldLeft(List[List[T]]()) {
      case (acc, t) if p(t) => List(t) :: acc
      case (Nil, t)         => List(t) :: Nil
      case (a :: as, t)     => (t :: a) :: as
    }.map(_.reverse).reverse

    groupedOn(stories) { case DataRow.Chapter(_) => true; case _ => false }.reverse.flatMap {
      case h :: t => h :: t.reverse
      case Nil    => Nil
    }

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
