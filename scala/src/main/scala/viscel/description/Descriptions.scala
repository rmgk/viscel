package viscel.description

import org.scalactic.{ErrorMessage, Every, Or}
import viscel.crawler._

import scala.collection.immutable.Map
import scala.language.implicitConversions

sealed trait Description

object Description {
	def fromOr(or: List[Description] Or Every[ErrorMessage]): List[Description] = or.fold(Predef.identity, FailedDescription(_) :: Nil)

	final case class Pointer(loc: AbsUri, pagetype: String) extends Description
	final case class Chapter(name: String, metadata: Map[String, String] = Map()) extends Description
	final case class Asset(source: AbsUri, origin: AbsUri, metadata: Map[String, String] = Map()) extends Description
	final case class CoreDescription(kind: String, id: String, name: String, metadata: Map[String, String]) extends Description
	final case class FailedDescription(reason: Every[ErrorMessage]) extends Description
}
