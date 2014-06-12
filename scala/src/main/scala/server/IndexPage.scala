package viscel.server

import scalatags._
import viscel.store.UserNode
import viscel.store.{ Util => StoreUtil }
import scala.collection.JavaConversions._

class IndexPage(user: UserNode) extends HtmlPage {
	override def Title = "Index"
	override def bodyId = "index"

	override def sidePart = make_fieldset("Search", Seq(form_search(""))).cls("info")

	override def navigation = link_stop("stop")

	override def mainPart = {
		val bookmarks = user.bookmarks.toIndexedSeq
		val (unread, current) = bookmarks.map { bm => (bm.collection, bm.collection.name, bm.distanceToLast) }.partition { _._3 > 0 }
		val unreadTags = unread.sortBy { -_._3 }.map { case (id, name, unread) => link_node(id, s"$name ($unread)") }
		val currentTags = current.sortBy { _._2 }.map { case (id, name, unread) => link_node(id, s"$name") }

		Seq(
			make_fieldset("New Pages", unreadTags).cls("group"),
			make_fieldset("Bookmarks", currentTags).cls("group"))
	}

	override def content: STag = body.id(bodyId)(
		div.cls("main")(mainPart),
		div.cls("side")(sidePart),
		div.cls("navigation")(navigation))
}

object IndexPage {
	def apply(user: UserNode) = new IndexPage(user).response
}
