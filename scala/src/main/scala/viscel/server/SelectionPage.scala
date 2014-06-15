package viscel.server

import viscel.store.UserNode

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
		//						Seq[Node](input.ctype("checkbox").name(id).value("select").pipe {
		//							case inp if known.contains(id) => inp.attr("checked" -> "checked")
		//							case inp => inp
		//						}, if (stored(id)) b(id) else id, <br/>)
		//					}
		//			},
		//				input.ctype("submit").name("select_cores").value("apply"),
		//				input.ctype("reset").value("reset")))
	}

	def navigation = link_main("index") :: Nil

	def sidePart = "" :: Nil
}

/*<form action="select.htm">
  <p>
    <select name="top5" size="5" multiple>
      <option>Heino</option>
      <option>Michael Jackson</option>
      <option>Tom Waits</option>
      <option>Nina Hagen</option>
      <option>Marianne Rosenberg</option>
    </select>
  </p>
  </form>*/

object SelectionPage {
	def apply(user: UserNode) = new SelectionPage(user).response
}
