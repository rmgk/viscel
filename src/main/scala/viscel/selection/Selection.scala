package viscel.selection

import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import org.scalactic.Accumulation.convertGenTraversableOnceToValidatable
import org.scalactic.{Bad, Every, Good, One, Or}

import scala.collection.JavaConverters.iterableAsScalaIterableConverter

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
	def wrap[R](fun: List[Element] => R Or Every[Report]): R Or Every[Report]
	/** wrap the single selected element into a result */
	def wrapOne[R](fun: Element => R Or Every[Report]): R Or Every[Report]
	/** wrap each element into a result and return a list of these results */
	def wrapEach[R](fun: Element => R Or Every[Report]): List[R] Or Every[Report]
	/** wrap each element into a list of results, return the concatenation of these lists */
	def wrapFlat[R](fun: Element => List[R] Or Every[Report]): List[R] Or Every[Report]
	/** get the elements */
	def get: List[Element] Or Every[Report]
	/** get the single element */
	def getOne: Element Or Every[Report]
	/** reverse the list of elements */
	def reverse: Selection
}

object Selection {
	def apply(element: Element): Selection = new GoodSelection(List(element))
	def apply(elements: List[Element]): Selection = new GoodSelection(elements)
}

case class GoodSelection(elements: List[Element]) extends Selection {

	def validateQuery[R](query: String)(validate: Elements => R Or Report): List[R] Or Every[Report] = {
		elements.validatedBy { element =>
			validate(element.select(query.trim)).badMap {FailedElement(query, _, element)}.accumulating
		}
	}

	override def unique(query: String): Selection = {
		validateQuery(query) {
			case rs if rs.size > 1 => Bad(QueryNotUnique)
			case rs if rs.size < 1 => Bad(QueryNotMatch)
			case rs => Good(rs.get(0))
		}.fold(GoodSelection, BadSelection)
	}

	override def many(query: String): Selection = {
		validateQuery(query) {
			case rs if rs.size < 1 => Bad(QueryNotMatch)
			case rs => Good(rs.asScala.toList)
		}.fold(good => GoodSelection(good.flatten), BadSelection)
	}

	override def all(query: String): Selection = {
		validateQuery(query) { rs => Good(rs.asScala.toList) }
			.fold(good => GoodSelection(good.flatten), BadSelection)
	}

	override def optional(query: String): Selection = {
		validateQuery(query) {
			case rs if rs.size > 1 => Bad(QueryNotUnique)
			case rs => Good(rs.asScala.toList)
		}.fold(good => GoodSelection(good.flatten), BadSelection)
	}

	/** selects the first matching element */
	override def first(query: String): Selection = {
		validateQuery(query) {
			case rs if rs.size < 1 => Bad(QueryNotMatch)
			case rs => Good(rs.get(0))
		}.fold(GoodSelection, BadSelection)
	}


	override def wrap[R](fun: List[Element] => R Or Every[Report]): R Or Every[Report] = fun(elements)

	override def wrapOne[R](fun: Element => R Or Every[Report]): R Or Every[Report] = {
		elements match {
			case els if els.isEmpty => Bad(One(Fatal("wrapOne on no result")))
			case els if els.size == 1 => fun(els.head)
			case els => Bad(One(Fatal("wrapOne on many results")))
		}
	}

	override def get: Or[List[Element], Every[Report]] = wrap(Good(_))
	override def getOne: Or[Element, Every[Report]] = wrapOne(Good(_))
	override def wrapEach[R](fun: (Element) => Or[R, Every[Report]]): Or[List[R], Every[Report]] = elements.validatedBy {fun}
	override def wrapFlat[R](fun: (Element) => Or[List[R], Every[Report]]): Or[List[R], Every[Report]] = wrapEach(fun).map(_.flatten)

	override def reverse: GoodSelection = GoodSelection(elements.reverse)
}

case class BadSelection(errors: Every[Report]) extends Selection {
	override def unique(query: String): BadSelection = this
	override def many(query: String): BadSelection = this
	override def all(query: String): Selection = this
	override def first(query: String): Selection = this
	override def wrap[R](fun: List[Element] => R Or Every[Report]): R Or Every[Report] = Bad(errors)
	override def wrapOne[R](fun: Element => R Or Every[Report]): R Or Every[Report] = Bad(errors)
	override def wrapEach[R](fun: (Element) => Or[R, Every[Report]]): Or[List[R], Every[Report]] = Bad(errors)
	override def reverse: BadSelection = this
	override def get: Or[List[Element], Every[Report]] = Bad(errors)
	override def getOne: Or[Element, Every[Report]] = Bad(errors)
	override def optional(query: String): Selection = this
	override def wrapFlat[R](fun: (Element) => Or[List[R], Every[Report]]): Or[List[R], Every[Report]] = Bad(errors)
}
