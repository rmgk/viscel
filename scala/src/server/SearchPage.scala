package viscel.server

import viscel.store.UserNode
import viscel.store.{ Util => StoreUtil }

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
