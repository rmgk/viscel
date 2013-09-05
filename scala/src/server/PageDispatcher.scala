package viscel.server

import com.typesafe.scalalogging.slf4j.Logging
import scala.xml.Elem
import scala.xml.Node
import scala.xml.NodeSeq
import scalatags._
import spray.http.{ HttpResponse, HttpEntity, MediaTypes, ContentType, HttpCharsets }
import viscel.store._
import viscel.time

object PageDispatcher {
	def apply(user: UserNode, vn: ViscelNode) = vn match {
		case n: ChapterNode => ChapterPage(user, n.collection)
		case n: CollectionNode => FrontPage(user, n)
		//case n: ChapteredCollectionNode => ChapteredFrontPage(user, n)
		case n: ElementNode => ViewPage(user, n)
		//case n: UserNode => IndexPage(user)
	}
}
