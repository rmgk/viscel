package viscel.serverStaticPages.pages

import viscel.database.Ntx
import viscel.serverStaticPages.HtmlPage
import viscel.database.Util.search
import viscel.store.User

import scalatags.Text.all._

class SearchPage(user: User, text: String)(implicit ntx: Ntx) extends HtmlPage {
	override def Title = "Search"
	override def bodyId = "search"

	def mainPart = make_fieldset("Search", Seq(form_search(text)))(class_info) :: Nil

	def navigation: Seq[Frag] = link_main("index") :: Nil

	def sidePart: List[Frag] = {
		val containing = search(text)
			.map { cn => link_core(cn) }
		make_fieldset(text, containing)(class_group) :: Nil
	}
}
