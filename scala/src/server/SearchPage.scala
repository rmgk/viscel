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

	def content = {
		val containing = StoreUtil.search(text)
			.map { cn => link_node(cn, cn.name) }

		body.id("search")(
			makeFieldset("Search", Seq(searchForm(text))).cls("info"),
			div.cls("navigation")(
				link_main("index")),
			makeFieldset(text, containing).cls("group"))
	}

	def searchForm = form_get(path_search, input.ctype("textfield").name("q"))

	def makeFieldset(name: String, entries: Seq[STag]) = fieldset(legend(name), entries.flatMap { e => Seq[STag](e, <br/>) })
}

object SearchPage {
	def apply(user: UserNode, text: String) = new SearchPage(user, text).response
}
