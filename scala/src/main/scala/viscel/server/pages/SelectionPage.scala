package viscel.server.pages

import viscel.server.HtmlPage
import viscel.store.nodes.UserNode

import scalatags.Text.all._

class SelectionPage(user: UserNode) extends HtmlPage {
	override def Title = "Select"

	override def bodyId = "select"

	def mainPart = {
		"nothing to see, move along" :: Nil
		//		val known = ConfigNode().legacyCollections
		//		fieldset.cls("info")(legend("Select Cores"),
		//			form_post("select", {
		//				val stored = Neo.txs { viscel.store.Util.list.map { _.id }.toSet }
		//				viscel.core.Clockwork.availableCores.map { _.id }.toSeq.sorted
		//					.map { id =>
		//						Seq[Frag](input.ctype("checkbox").name(id).value("select").pipe {
		//							case inp if known.contains(id) => inp.attr("checked" -> "checked")
		//							case inp => inp
		//						}, if (stored(id)) b(id) else id, <br/>)
		//					}
		//			},
		//				input.ctype("submit").name("select_cores").value("apply"),
		//				input.ctype("reset").value("reset")))
	}

	def navigation: Seq[Frag] = link_main("index") :: Nil

	def sidePart = "" :: Nil
}
