package viscel.compat.v1

import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import org.scalactic.Accumulation.convertGenTraversableOnceToValidatable
import org.scalactic.{Bad, ErrorMessage, Every, Good, One, Or}
import viscel.narration.SelectUtilV1.{blame, caller, extract}

import scala.Predef.$conforms
import scala.collection.JavaConverters.iterableAsScalaIterableConverter

sealed trait SelectionV1 {
	/** select exactly one element */
	def unique(query: String): SelectionV1
	/** select one ore more elements */
	def many(query: String): SelectionV1
	/** select zero or one element */
	def optional(query: String): SelectionV1
	/** select any number of elements */
	def all(query: String): SelectionV1
	/** selects the first matching element */
	def first(query: String): SelectionV1
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
	def reverse: SelectionV1
}

object SelectionV1 {
	def apply(element: Element): SelectionV1 = new GoodSelectionV1(List(element))
	def apply(elements: List[Element]): SelectionV1 = new GoodSelectionV1(elements)
}

case class GoodSelectionV1(elements: List[Element]) extends SelectionV1 {

	def validateQuery[R](query: String)(validate: Elements => R Or ErrorMessage): List[R] Or Every[ErrorMessage] = {
		elements.validatedBy { element =>
			extract { element.select(query) }.flatMap { res =>
				validate(res).badMap { err =>
					blame(s"$err ($query)", element)
				}.accumulating
			}
		}
	}

	override def unique(query: String): SelectionV1 = {
		validateQuery(query) {
			case rs if rs.size > 1 => Bad("query not unique")
			case rs if rs.size < 1 => Bad("query not found)")
			case rs => Good(rs.get(0))
		}.fold(GoodSelectionV1, BadSelectionV1)
	}

	override def many(query: String): SelectionV1 = {
		validateQuery(query) {
			case rs if rs.size < 1 => Bad("query did not match")
			case rs => Good(rs.asScala.toList)
		}.fold(good => GoodSelectionV1(good.flatten), BadSelectionV1)
	}

	override def all(query: String): SelectionV1 = {
		validateQuery(query) { rs => Good(rs.asScala.toList) }
			.fold(good => GoodSelectionV1(good.flatten), BadSelectionV1)
	}

	override def optional(query: String): SelectionV1 = {
		validateQuery(query) {
			case rs if rs.size > 1 => Bad(s"query not unique ")
			case rs => Good(rs.asScala.toList)
		}.fold(good => GoodSelectionV1(good.flatten), BadSelectionV1)
	}

	/** selects the first matching element */
	override def first(query: String): SelectionV1 = {
		validateQuery(query) {
			case rs if rs.size < 1 => Bad("query did not match")
			case rs => Good(rs.get(0))
		}.fold(GoodSelectionV1, BadSelectionV1)
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
	override def wrapEach[R](fun: (Element) => Or[R, Every[ErrorMessage]]): Or[List[R], Every[ErrorMessage]] = elements.validatedBy { fun }
	override def wrapFlat[R](fun: (Element) => Or[List[R], Every[ErrorMessage]]): Or[List[R], Every[ErrorMessage]] = wrapEach(fun).map(_.flatten)

	override def reverse: GoodSelectionV1 = GoodSelectionV1(elements.reverse)
}

case class BadSelectionV1(errors: Every[ErrorMessage]) extends SelectionV1 {
	override def unique(query: String): BadSelectionV1 = this
	override def many(query: String): BadSelectionV1 = this
	override def all(query: String): SelectionV1 = this
	override def first(query: String): SelectionV1 = this
	override def wrap[R](fun: List[Element] => R Or Every[ErrorMessage]): R Or Every[ErrorMessage] = Bad(errors)
	override def wrapOne[R](fun: Element => R Or Every[ErrorMessage]): R Or Every[ErrorMessage] = Bad(errors)
	override def wrapEach[R](fun: (Element) => Or[R, Every[ErrorMessage]]): Or[List[R], Every[ErrorMessage]] = Bad(errors)
	override def reverse: BadSelectionV1 = this
	override def get: Or[List[Element], Every[ErrorMessage]] = Bad(errors)
	override def getOne: Or[Element, Every[ErrorMessage]] = Bad(errors)
	override def optional(query: String): SelectionV1 = this
	override def wrapFlat[R](fun: (Element) => Or[List[R], Every[ErrorMessage]]): Or[List[R], Every[ErrorMessage]] = Bad(errors)
}
