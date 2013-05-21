package viscel.collection

import viscel.Element
import viscel.display.OrderedDisplay

trait OrderedCollection extends OrderedDisplay {
	def get(index: Int): Element
	def last: Int
	def name: String
}
