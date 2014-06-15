package viscel.server

import viscel.store.{UserNode, Util => StoreUtil}

import scalatags._
import scalatags.all._

class IndexPage(user: UserNode) extends HtmlPage {
	override def Title = "Index"
	override def bodyId = "index"

	override def sidePart = make_fieldset("Search", Seq(form_search("")))(class_info) :: Nil

	override def navigation = link_stop("stop") :: Nil

	override def mainPart = {
		val bookmarks = user.bookmarks.toIndexedSeq
		val (unread, current) = bookmarks.map { bm => (bm.collection, bm.collection.name, bm.distanceToLast) }.partition { _._3 > 0 }
		val unreadTags = unread.sortBy { -_._3 }.map { case (id, name, unread) => link_node(id, s"$name ($unread)") }
		val currentTags = current.sortBy { _._2 }.map { case (id, name, unread) => link_node(id, s"$name") }

		Seq(
			make_fieldset("New Pages", unreadTags)(class_group),
			make_fieldset("Bookmarks", currentTags)(class_group))
	}

	override def content: Node = body(id := bodyId)(
		div(class_main)(mainPart),
		div(class_side)(sidePart),
		div(class_navigation)(navigation))
}

object IndexPage {
	def apply(user: UserNode) = new IndexPage(user).response
}
