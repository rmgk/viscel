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
import viscel.store.ConfigNode
import viscel.store.ViscelNode
import viscel.store.{ Util => StoreUtil }
import viscel.time
import viscel._

class SelectionPage(user: UserNode) extends HtmlPage {
	override def Title = "Select"
	override def bodyId = "select"

	def mainPart = {
		val known = ConfigNode().legacyCollections
		fieldset.cls("info")(legend("Select Cores"),
			form_post("select",
				viscel.core.LegacyCores.list.map { _.id }.sorted
					.map { id =>
						Seq[STag](input.ctype("checkbox").name(id).value("select").pipe {
							case inp if known.contains(id) => inp.attr("checked" -> "checked")
							case inp => inp
						}, id, <br/>)
					},
				input.ctype("submit").name("select_cores").value("apply"),
				input.ctype("reset").value("reset")))
	}

	def navigation = link_main("index")

	def sidePart = ""
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
