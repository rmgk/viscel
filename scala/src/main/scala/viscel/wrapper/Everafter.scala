package viscel.wrapper

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.jsoup.nodes.{Element, Document}
import org.scalactic.Accumulation._
import org.scalactic._
import viscel.core._

import scala.collection.JavaConversions._
import scala.util.Try

trait Selection {
	def unique(query: String): Selection
	def all(query: String): Selection
	def wrap[R](fun: Seq[Element] => R Or Every[ErrorMessage]): R Or Every[ErrorMessage]
	def wrapOne[R](fun: Element => R Or Every[ErrorMessage]): R Or Every[ErrorMessage]

	def caller(n: Int) = {
		val c = Thread.currentThread().getStackTrace()(n)
		s"${ c.getClassName }#${ c.getMethodName }:${ c.getLineNumber }"
	}
}

object Selection {
	def apply(element: Element) = new GoodSelection(Vector(element))
	def apply(elements: Seq[Element]) = new GoodSelection(elements)
}

case class GoodSelection(elements: Seq[Element]) extends Selection {

	override def unique(query: String): Selection = {
		elements.validatedBy { element =>
			element.select(query) match {
				case rs if rs.size > 2 => Bad(One(s"query not unique ($query) at (${ caller(6) }) on (${ element.baseUri }, ${ element.tag }, #${ element.id }, .${ element.classNames })"))
				case rs if rs.size < 1 => Bad(One(s"query not found ($query) at (${ caller(6) }) on (${ element.baseUri }, ${ element.tag }, #${ element.id }, .${ element.classNames })"))
				case rs => Good(rs(0))
			}
		}.fold(GoodSelection, BadSelection)
	}

	override def all(query: String): Selection = {
		elements.validatedBy { from =>
			from.select(query) match {
				case rs if rs.size < 1 => Bad(One(s"query did not match ($query) at (${ caller(6) }) on (${ from.baseUri }, ${ from.tag }, #${ from.id }, .${ from.classNames })"))
				case rs => Good(rs.toIndexedSeq)
			}
		}.fold(good => GoodSelection(good.flatten), BadSelection)
	}

	override def wrap[R](fun: Seq[Element] => R Or Every[ErrorMessage]): R Or Every[ErrorMessage] = fun(elements)

	override def wrapOne[R](fun: Element => R Or Every[ErrorMessage]): R Or Every[ErrorMessage] =
		elements.headOption.map{fun}.getOrElse(Bad(One("does not have one element")))
}

case class BadSelection(errors: Every[ErrorMessage]) extends Selection {
	override def unique(query: String): Selection = this
	override def all(query: String): Selection = this
	override def wrap[R](fun: Seq[Element] => R Or Every[ErrorMessage]): R Or Every[ErrorMessage] = Bad(errors)
	override def wrapOne[R](fun: Element => R Or Every[ErrorMessage]): R Or Every[ErrorMessage] = Bad(errors)
}

object Everafter extends Core with WrapperTools with StrictLogging {
	def archive = StructureDescription( next = PointerDescription("http://ea.snafu-comics.com/archive.php", "archive"),
		payload = ChapterContent("No Chapters?!"))

	def id: String = "Snafu_Everafter"

	def name: String = "Everafter"

	def wrapArchive(doc: Document, pd: PointerDescription): Description Or Every[ErrorMessage] = {
		Selection(doc).unique(".pagecontentbox").all("a").wrap { anchors => anchorsIntoPointers("page")(anchors.reverse) }
	}

	def wrapPage(doc: Document, pd: PointerDescription): StructureDescription Or Every[ErrorMessage] = {
		Selection(doc).unique("img[src~=comics/\\d{6}]").wrapOne {img => Good(StructureDescription(payload = imgToElement(img)))}
	}

	def wrap(doc: Document, pd: PointerDescription): Description = Description.fromOr(pd.pagetype match {
		case "archive" => wrapArchive(doc, pd)
		case "page" => wrapPage(doc, pd)
	})
}
