package viscel.server

import viscel.store.{UserNode, Util => StoreUtil}

import scalatags.Text.all._

class SearchPage(user: UserNode, text: String) extends HtmlPage {
	override def Title = "Search"
	override def bodyId = "search"

	def mainPart = make_fieldset("Search", Seq(form_search(text)))(class_info) :: Nil

	def navigation: Seq[Frag] = link_main("index") :: Nil

	def sidePart: List[Frag] = {
		val containing = StoreUtil.search(text)
			.map { cn => link_core(cn) }
		make_fieldset(text, containing)(class_group) :: Nil
	}
}

object SearchPage {
	def apply(user: UserNode, text: String) = new SearchPage(user, text).response
}
