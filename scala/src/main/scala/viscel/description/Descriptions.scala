package viscel.description

import org.scalactic.TypeCheckedTripleEquals._
import org.scalactic.{ErrorMessage, Every, Or}
import viscel.core._
import viscel.store._
import scala.language.implicitConversions


sealed trait Description

object Description {
	implicit def fromOr(or: List[Description] Or Every[ErrorMessage]): List[Description] = or.fold(identity, FailedDescription(_) :: Nil)
}

case class Pointer(loc: AbsUri, pagetype: String) extends Description
case class Chapter(name: String, metadata: Map[String, String] = Map()) extends Description
case class Asset(source: AbsUri, origin: AbsUri, metadata: Map[String, String] = Map()) extends Description
case class CoreDescription(kind: String, id: String, name: String, metadata: Map[String, String]) extends Description
case class FailedDescription(reason: Every[ErrorMessage]) extends Description
