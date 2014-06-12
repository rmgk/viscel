package viscel.newCore

import spray.http.ContentType
import spray.http.HttpResponse

sealed trait Payload

case class ChapterDescription(name: String, props: Map[String, String] = Map()) extends Payload
case class ElementDescription(
	source: AbsUri,
	origin: AbsUri,
	props: Map[String, String] = Map()) extends Payload

case class ElementData(mediatype: ContentType, sha1: String, buffer: Array[Byte], response: HttpResponse, description: ElementDescription)

sealed trait Description
case class PointerDescription(loc: AbsUri, pagetype: String) extends Description
case object EmptyDescription extends Description with Payload
case class FailedDescription(reason: Throwable) extends Description
case class StructureDescription(payload: Payload = EmptyDescription, next: Description = EmptyDescription, children: Seq[Description] = Seq()) extends Description
