package viscel.wrapper

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.jsoup.nodes.{Element, Document}
import org.jsoup.select.Elements
import org.scalactic.Accumulation._
import org.scalactic._
import viscel.core._
import viscel.wrapper.Util._

import scala.collection.JavaConversions._
import scala.util.Try

trait Selection {
	def unique(query: String): Selection
	def many(query: String): Selection
	def optional(query: String): Selection
	def all(query: String): Selection
	def wrap[R](fun: Seq[Element] => R Or Every[ErrorMessage]): R Or Every[ErrorMessage]
	def wrapOne[R](fun: Element => R Or Every[ErrorMessage]): R Or Every[ErrorMessage]
	def wrapEach[R](fun: Element => R Or Every[ErrorMessage]): Seq[R] Or Every[ErrorMessage]
	def get: Seq[Element] Or Every[ErrorMessage]
	def getOne: Element Or Every[ErrorMessage]
	def reverse: Selection
}

object Selection {
	def apply(element: Element) = new GoodSelection(Vector(element))
	def apply(elements: Seq[Element]) = new GoodSelection(elements)
}

case class GoodSelection(elements: Seq[Element]) extends Selection {

	def validateQuery[R](query: String)(validate: Elements => R Or ErrorMessage): Seq[R] Or Every[ErrorMessage] = {
		elements.validatedBy { element =>
			extract { element.select(query) }.flatMap { res =>
				validate(res).badMap { err =>
					blame(s"$err ($query)", element)
				}.accumulating
			}
		}
	}

	override def unique(query: String): Selection = {
		validateQuery(query) {
			case rs if rs.size > 2 => Bad("query not unique")
			case rs if rs.size < 1 => Bad("query not found)")
			case rs => Good(rs(0))
		}.fold(GoodSelection, BadSelection)
	}

	override def many(query: String): Selection = {
		validateQuery(query) {
			case rs if rs.size < 1 => Bad("query did not match")
			case rs => Good(rs.toIndexedSeq)
		}.fold(good => GoodSelection(good.flatten), BadSelection)
	}

	override def all(query: String): Selection = {
		validateQuery(query) { rs => Good(rs.toIndexedSeq) }
			.fold(good => GoodSelection(good.flatten), BadSelection)
	}

	override def optional(query: String): Selection = {
		validateQuery(query) {
				case rs if rs.size > 2 => Bad(s"query not unique ")
				case rs => Good(rs.toIndexedSeq)
		}.fold(good => GoodSelection(good.flatten), BadSelection)
	}

	override def wrap[R](fun: Seq[Element] => R Or Every[ErrorMessage]): R Or Every[ErrorMessage] = fun(elements)

	override def wrapOne[R](fun: Element => R Or Every[ErrorMessage]): R Or Every[ErrorMessage] = {
		elements match {
			case els if els.isEmpty => Bad(One(s"selection has no elements at $caller"))
			case els if els.size == 1 => fun(els.head)
			case els => Bad(One(blame(s"selection has multiple elements", els: _*)))
		}
	}

	override def get: Or[Seq[Element], Every[ErrorMessage]] = wrap(Good(_))
	override def getOne: Or[Element, Every[ErrorMessage]] = wrapOne(Good(_))
	override def wrapEach[R](fun: (Element) => Or[R, Every[ErrorMessage]]): Or[Seq[R], Every[ErrorMessage]] = elements.validatedBy{fun}

	override def reverse: GoodSelection = GoodSelection(elements.reverse)
}

case class BadSelection(errors: Every[ErrorMessage]) extends Selection {
	override def unique(query: String): BadSelection = this
	override def many(query: String): BadSelection = this
	override def all(query: String): Selection = this
	override def wrap[R](fun: Seq[Element] => R Or Every[ErrorMessage]): R Or Every[ErrorMessage] = Bad(errors)
	override def wrapOne[R](fun: Element => R Or Every[ErrorMessage]): R Or Every[ErrorMessage] = Bad(errors)
	override def wrapEach[R](fun: (Element) => Or[R, Every[ErrorMessage]]): Or[Seq[R], Every[ErrorMessage]] = Bad(errors)
	override def reverse: BadSelection = this
	override def get: Or[Seq[Element], Every[ErrorMessage]] = Bad(errors)
	override def getOne: Or[Element, Every[ErrorMessage]] = Bad(errors)
	override def optional(query: String): Selection = this
}
