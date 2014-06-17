package viscel.core.impl

import com.typesafe.scalalogging.slf4j.StrictLogging
import viscel.core._
import viscel.store._
import viscel.description._

trait ArchiveCreator extends StrictLogging {

	def create(desc: Description): Option[ArchiveNode] = {
		logger.trace(s"create $desc")
		desc match {
			case Pointer(loc, pagetype) => Some { PageNode.create(loc, pagetype) }
			case EmptyDescription => None
			case FailedDescription(reason) => None
			case Structure(payload, next, children) =>
				val payNode = createPayload(payload)
				val childNodes = children.flatMap { create }
				val nextNode = create(next)
				Some(StructureNode.create(payNode, nextNode, childNodes))
		}
	}

	def createPayload(payload: Content): Option[ViscelNode] = payload match {
		case Chapter(name, props) =>
			Some(ChapterNode.create(name, props.to[Seq]: _*))
		case ElementContent(source, origin, props) =>
			Some(ElementNode.create(source = source, origin = origin, props))
		case EmptyContent => None
	}
}
