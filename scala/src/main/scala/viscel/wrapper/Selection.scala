package viscel.wrapper

import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import org.scalactic.Accumulation._
import org.scalactic._
import viscel.wrapper.Util._

import scala.Predef.conforms
import scala.collection.JavaConverters._

sealed trait Selection {
	/** select exactly one element */
	def unique(query: String): Selection
	/** select one ore more elements */
	def many(query: String): Selection
	/** select zero or one element */
	def optional(query: String): Selection
	/** select any number of elements */
	def all(query: String): Selection
	/** selects the first matching element */
	def first(query: String): Selection
	/** wrap the list of elements into a result */
	def wrap[R](fun: List[Element] => R Or Every[ErrorMessage]): R Or Every[ErrorMessage]
	/** wrap the single selected element into a result */
	def wrapOne[R](fun: Element => R Or Every[ErrorMessage]): R Or Every[ErrorMessage]
	/** wrap each element into a result and return a list of these results */
	def wrapEach[R](fun: Element => R Or Every[ErrorMessage]): List[R] Or Every[ErrorMessage]
	/** wrap each element into a list of results, return the concatenation of these lists */
	def wrapFlat[R](fun: Element => List[R] Or Every[ErrorMessage]): List[R] Or Every[ErrorMessage]
	/** get the elements */
	def get: List[Element] Or Every[ErrorMessage]
	/** get the single element */
	def getOne: Element Or Every[ErrorMessage]
	/** reverse the list of elements */
	def reverse: Selection
}

object Selection {
	def apply(element: Element) = new GoodSelection(List(element))
	def apply(elements: List[Element]) = new GoodSelection(elements)
}

case class GoodSelection(elements: List[Element]) extends Selection {

	def validateQuery[R](query: String)(validate: Elements => R Or ErrorMessage): List[R] Or Every[ErrorMessage] = {
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
			case rs if rs.size > 1 => Bad("query not unique")
			case rs if rs.size < 1 => Bad("query not found)")
			case rs => Good(rs.get(0))
		}.fold(GoodSelection, BadSelection)
	}

	override def many(query: String): Selection = {
		validateQuery(query) {
			case rs if rs.size < 1 => Bad("query did not match")
			case rs => Good(rs.asScala.toList)
		}.fold(good => GoodSelection(good.flatten), BadSelection)
	}

	override def all(query: String): Selection = {
		validateQuery(query) { rs => Good(rs.asScala.toList) }
			.fold(good => GoodSelection(good.flatten), BadSelection)
	}

	override def optional(query: String): Selection = {
		validateQuery(query) {
				case rs if rs.size > 1 => Bad(s"query not unique ")
				case rs => Good(rs.asScala.toList)
		}.fold(good => GoodSelection(good.flatten), BadSelection)
	}

	/** selects the first matching element */
	override def first(query: String): Selection = {
		validateQuery(query) {
			case rs if rs.size < 1 => Bad("query did not match")
			case rs => Good(rs.get(0))
		}.fold(GoodSelection, BadSelection)
	}


	override def wrap[R](fun: List[Element] => R Or Every[ErrorMessage]): R Or Every[ErrorMessage] = fun(elements)

	override def wrapOne[R](fun: Element => R Or Every[ErrorMessage]): R Or Every[ErrorMessage] = {
		elements match {
			case els if els.isEmpty => Bad(One(s"selection has no elements at $caller"))
			case els if els.size == 1 => fun(els.head)
			case els => Bad(One(blame(s"selection has multiple elements", els: _*)))
		}
	}

	override def get: Or[List[Element], Every[ErrorMessage]] = wrap(Good(_))
	override def getOne: Or[Element, Every[ErrorMessage]] = wrapOne(Good(_))
	override def wrapEach[R](fun: (Element) => Or[R, Every[ErrorMessage]]): Or[List[R], Every[ErrorMessage]] = elements.validatedBy{fun}
	override def wrapFlat[R](fun: (Element) => Or[List[R], Every[ErrorMessage]]): Or[List[R], Every[ErrorMessage]] = wrapEach(fun).map(_.flatten)

	override def reverse: GoodSelection = GoodSelection(elements.reverse)
}

case class BadSelection(errors: Every[ErrorMessage]) extends Selection {
	override def unique(query: String): BadSelection = this
	override def many(query: String): BadSelection = this
	override def all(query: String): Selection = this
	override def first(query: String): Selection = this
	override def wrap[R](fun: List[Element] => R Or Every[ErrorMessage]): R Or Every[ErrorMessage] = Bad(errors)
	override def wrapOne[R](fun: Element => R Or Every[ErrorMessage]): R Or Every[ErrorMessage] = Bad(errors)
	override def wrapEach[R](fun: (Element) => Or[R, Every[ErrorMessage]]): Or[List[R], Every[ErrorMessage]] = Bad(errors)
	override def reverse: BadSelection = this
	override def get: Or[List[Element], Every[ErrorMessage]] = Bad(errors)
	override def getOne: Or[Element, Every[ErrorMessage]] = Bad(errors)
	override def optional(query: String): Selection = this
	override def wrapFlat[R](fun: (Element) => Or[List[R], Every[ErrorMessage]]): Or[List[R], Every[ErrorMessage]] = Bad(errors)
}
