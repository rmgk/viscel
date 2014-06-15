package viscel.core.impl

import com.typesafe.scalalogging.slf4j.StrictLogging
import viscel.core._
import viscel.store._

trait ArchiveCreator extends StrictLogging {

	def create(desc: Description): Option[ArchiveNode] = desc match {
		case PointerDescription(loc, pagetype) =>
			logger.debug(s"create: page node for $desc")
			Some { PageNode.create(loc, pagetype) }

		case EmptyDescription =>
			logger.debug("create: empty description")
			None

		case FailedDescription(reason) =>
			logger.warn(s"create: failed description $reason")
			None

		case StructureDescription(payload, next, children) =>
			val payNode = createPayload(payload)
			val childNodes = children.flatMap { create }
			val nextNode = create(next)
			Some(StructureNode.create(payNode, nextNode, childNodes))
	}
	def createPayload(payload: Content): Option[ViscelNode] = payload match {
		case ChapterContent(name, props) =>
			Some(ChapterNode.create(name, props.to[Seq]: _*))
		case ElementContent(source, origin, props) =>
			Some(ElementNode.create(source = source, origin = origin, props))
		case EmptyContent => None
	}
}
