package viscel.server

import com.typesafe.scalalogging.slf4j.Logging
import scala.xml.Elem
import scala.xml.Node
import scala.xml.NodeSeq
import scalatags._
import spray.http.{ HttpResponse, HttpEntity, MediaTypes, ContentType, HttpCharsets }
import viscel.store.CollectionNode
import viscel.store.ChapteredCollectionNode
import viscel.store.ElementNode
import viscel.store.Neo
import viscel.store.UserNode
import viscel.store.ViscelNode
import viscel.store.{ Util => StoreUtil }
import viscel.time

object PageDispatcher {
	def apply(user: UserNode, vn: ViscelNode) = vn match {
		//case n: ChapterNode =>
		case n: CollectionNode => FrontPage(user, n)
		// case n: ChapteredCollectionNode => ChapteredFrontPage(user, n)
		case n: ElementNode => ViewPage(user, n)
		//case n: UserNode => IndexPage(user)
	}
}
