package viscel.selektiv

import org.jsoup.internal.StringUtil
import org.jsoup.nodes.Element
import viscel.narration.Narrator.Wrapper
import viscel.selektiv.Narration.WrapPart
import viscel.selektiv.ReportTools.*
import viscel.shared.{DataRow, Vurl}

import scala.util.Try
import scala.util.matching.Regex

object Queries {

  /** tries to extract an absolute uri from an element, extraction depends on type of tag */
  def extractURL(element: Element): Vurl =
    element.tagName() match {
      case "a"      => extract { Vurl.fromString(element.attr("abs:href")) }
      case "link"   => extract { Vurl.fromString(element.attr("abs:href")) }
      case "option" => extract { Vurl.fromString(element.attr("abs:value")) }
      case _        => throw FailedElement(s"extract uri", UnhandledTag, element)
    }

  def selectMore(elements: List[Element]): List[DataRow.Link] =
    if (elements.isEmpty) Nil
    else
      elements.map(extractMore) match {
        case pointers if pointers.toSet.size == 1 => pointers.headOption.toList
        case pointers => throw FailedElement("next not unique", QueryNotUnique, elements*)
      }

  def extractMore(element: Element): DataRow.Link = DataRow.Link(extractURL(element))

  def extractArticle(img: Element): DataRow.Link = {
    imageFromAttribute(img, None)
  }

  def imageFromAttribute(img: Element, customAttr: Option[String]): DataRow.Link = {
    def getAttr(k: String): List[String] = {
      val res = img.attr(k)
      if (res.isEmpty) Nil else List(k, res)
    }

    img.tagName() match {
      case "img" =>
        extract(DataRow.Link(
          ref = Vurl.fromString(img.attr(customAttr.getOrElse("abs:src"))),
          data = List("alt", "title", "width", "height").flatMap(getAttr)
        ))
      case "embed" =>
        extract(DataRow.Link(
          ref = Vurl.fromString(img.attr(customAttr.getOrElse("abs:src"))),
          data = List("width", "height", "type").flatMap(getAttr)
        ))
      case "object" =>
        extract(DataRow.Link(
          ref = Vurl.fromString(img.attr(customAttr.getOrElse("abs:data"))),
          data = List("width", "height", "type").flatMap(getAttr)
        ))
      case _ =>
        customAttr match
          case Some(attr) =>
            extract(DataRow.Link(
              ref = Vurl.fromString(img.attr(attr)),
              data = List("width", "height", "type").flatMap(getAttr)
            ))
          case None =>
            val extractBG = """.*background-image:url\((?<url>[^)]+)\).*""".r
            img.attr("style") match {
              case extractBG(url) =>
                extract(DataRow.Link(
                  ref = Vurl.fromString(StringUtil.resolve(img.ownerDocument().location(), url))
                ))
              case _ => throw FailedElement(s"into article", UnhandledTag, img)
            }
    }
  }
  def extractChapter(elem: Element): DataRow.Chapter =
    extract {
      def notBlank(s: String): Boolean    = !s.matches("^\\s*$")
      def firstNotEmpty(choices: String*) = choices.find(notBlank).getOrElse("")

      DataRow.Chapter(firstNotEmpty(elem.text(), elem.attr("title"), elem.attr("alt")))
    }

  def extractParentMore(image: Element): List[DataRow.Link] = {
    Try { extractMore(image.parent()) }.toOption.toList
  }

  /** extracts article at query result
    * optionally extracts direct parent of query result as link
    */
  def queryImageInAnchor(query: String): Wrapper =
    Selection.unique(query).map { image =>
      extractArticle(image) :: extractParentMore(image)
    }

  def queryNext(query: String): WrapPart[List[DataRow.Link]] = Selection.all(query).map(selectMore)

  def extractMixedArchive(elem: Element): DataRow.Content = {
    if (elem.tagName() == "a") extractMore(elem)
    else if (elem.tagName() == "img") extractArticle(elem)
    else extractChapter(elem)
  }

  def queryMixedArchive(query: String): Wrapper = {
    Selection.many(query).map(_.map(extractMixedArchive).toList)
  }

  def queryChapterArchive(query: String): Wrapper = {
    Selection.many(query).map(_.toList.flatMap { chapter =>
      List(extractChapter(chapter), extractMore(chapter))
    })
  }

  def chapterReverse(stories: List[DataRow.Content], reverseInner: Boolean = false): List[DataRow.Content] = {
    def groupedOn[T](l: List[T])(p: T => Boolean): List[List[T]] =
      l.foldLeft(List[List[T]]()) {
        case (acc, t) if p(t) => List(t) :: acc
        case (Nil, t)         => List(t) :: Nil
        case (a :: as, t)     => (t :: a) :: as
      }.map(_.reverse).reverse

    groupedOn(stories) { case DataRow.Chapter(_) => true; case _ => false }.reverse.flatMap {
      case h :: t => h :: (if (reverseInner) t.reverse else t)
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
