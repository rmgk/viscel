package viscel.wrapper

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.jsoup.nodes.{Document, Element}
import org.scalactic.Accumulation._
import org.scalactic.TypeCheckedTripleEquals._
import org.scalactic._
import viscel.core._

import scala.collection.JavaConversions._
import scala.language.implicitConversions

trait WrapperTools extends StrictLogging {

	def getAttr(e: Element, k: String): Option[(String, String)] = {
		val res = e.attr(k)
		if (res.isEmpty) None else Some(k -> res)
	}

	def imgToElement(img: Element): ElementContent = ElementContent(
		source = img.attr("abs:src"),
		origin = img.baseUri,
		props = (getAttr(img, "alt") ++
			getAttr(img, "title") ++
			getAttr(img, "width") ++
			getAttr(img, "height")).toMap)

	def caller(n: Int) = {
		val c = Thread.currentThread().getStackTrace()(n)
		s"${ c.getClassName }#${ c.getMethodName }:${ c.getLineNumber }"
	}

	def selectUnique(from: Element, query: String): Element Or One[ErrorMessage] = from.select(query) match {
		case rs if rs.size > 2 => Bad(One(s"query not unique ($query) at (${ caller(5) }) on (${ from.baseUri }, ${ from.tag }, #${ from.id }, .${ from.classNames })"))
		case rs if rs.size < 1 => Bad(One(s"query not found ($query) at (${ caller(5) }) on (${ from.baseUri }, ${ from.tag }, #${ from.id }, .${ from.classNames })"))
		case rs => Good(rs(0))
	}

	def selectSome(from: Element, query: String): Seq[Element] Or One[ErrorMessage] = from.select(query) match {
		case rs if rs.size < 1 => Bad(One(s"query did not match ($query) at (${ caller(5) }) on (${ from.baseUri }, ${ from.tag }, #${ from.id }, .${ from.classNames })"))
		case rs => Good(rs.toIndexedSeq)
	}

	def findParentTag(from: Element, tagname: String): Element Or One[ErrorMessage] =
		(from +: from.parents).find(_.tag.getName === tagname).fold(
			ifEmpty = Bad(One(s"$from has no parent $tagname")): Element Or One[ErrorMessage]
		)(Good(_))

	def chapter(name: String, children: Seq[Description] = Seq(), next: Description = EmptyDescription, props: Map[String, String] = Map()): StructureDescription =
		StructureDescription(
			payload = ChapterContent(name, props),
			children = children,
			next = next)
}




