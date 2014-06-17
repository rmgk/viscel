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
}

object Selection {
	def apply(element: Element) = new GoodSelection(Vector(element))
	def apply(elements: Seq[Element]) = new GoodSelection(elements)

	object Util {
		val ignoredClasses = Set("viscel.wrapper.Selection", "java.lang.Thread", "viscel.wrapper.GoodSelection", "org.scalactic", "scala")
		def caller: String = {
			val stackTraceOption = Thread.currentThread().getStackTrace().find { ste =>
				val cname = ste.getClassName
				!ignoredClasses.exists(cname.startsWith)
			}
			stackTraceOption.fold("invalid stacktrace"){ste => s"${ ste.getClassName }#${ ste.getMethodName }:${ ste.getLineNumber }" }
		}

		def show(element: Element) = s"${ element.tag }, #${ element.id }, .${ element.classNames }"
	}
}

case class GoodSelection(elements: Seq[Element]) extends Selection {
	import viscel.wrapper.Selection.Util._

	override def unique(query: String): Selection = {
		elements.validatedBy { element =>
			element.select(query) match {
				case rs if rs.size > 2 => Bad(One(s"query not unique ($query) at ($caller) on (${ element.baseUri }, ${show(element)})"))
				case rs if rs.size < 1 => Bad(One(s"query not found ($query) at ($caller) on (${ element.baseUri }, ${show(element)})"))
				case rs => Good(rs(0))
			}
		}.fold(GoodSelection, BadSelection)
	}

	override def all(query: String): Selection = {
		elements.validatedBy { from =>
			from.select(query) match {
				case rs if rs.size < 1 => Bad(One(s"query did not match ($query) at ($caller) on (${ from.baseUri },${show(from)})"))
				case rs => Good(rs.toIndexedSeq)
			}
		}.fold(good => GoodSelection(good.flatten), BadSelection)
	}

	override def wrap[R](fun: Seq[Element] => R Or Every[ErrorMessage]): R Or Every[ErrorMessage] = fun(elements)

	override def wrapOne[R](fun: Element => R Or Every[ErrorMessage]): R Or Every[ErrorMessage] = {
		elements match {
			case els if els.isEmpty => Bad(One(s"selection has no elements at $caller"))
			case els if els.size == 1 => fun(els.head)
			case els => Bad(One(s"selection has multiple elements at ($caller) on (${els.head.baseUri()}) elements ${els.map{show}}"))
		}
	}
}

case class BadSelection(errors: Every[ErrorMessage]) extends Selection {
	override def unique(query: String): Selection = this
	override def all(query: String): Selection = this
	override def wrap[R](fun: Seq[Element] => R Or Every[ErrorMessage]): R Or Every[ErrorMessage] = Bad(errors)
	override def wrapOne[R](fun: Element => R Or Every[ErrorMessage]): R Or Every[ErrorMessage] = Bad(errors)
}
