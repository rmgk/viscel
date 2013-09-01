package viscel.server

import com.typesafe.scalalogging.slf4j.Logging
import scala.xml.Elem
import scala.xml.Node
import scala.xml.NodeSeq
import scalatags._
import spray.http.{ HttpResponse, HttpEntity, MediaTypes, ContentType, HttpCharsets }
import viscel.store.CollectionNode
import viscel.store.ElementNode
import viscel.store.Neo
import viscel.store.UserNode
import viscel.store.ViscelNode
import viscel.store.{ Util => StoreUtil }
import viscel.time

class SearchPage(user: UserNode, text: String) extends HtmlPage {
	override def Title = "Search"
	override def bodyId = "search"

	def mainPart = make_fieldset("Search", Seq(form_search(text))).cls("info")

	def navigation = link_main("index")

	def sidePart = {
		val containing = StoreUtil.search(text)
			.map { cn => link_node(cn, cn.name) }
		make_fieldset(text, containing).cls("group")
	}
}

object SearchPage {
	def apply(user: UserNode, text: String) = new SearchPage(user, text).response
}
