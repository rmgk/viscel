package viscel.shared

import scala.language.implicitConversions

class ViscelUrl(val self: String) extends AnyVal {
	override def toString: String = self
}
