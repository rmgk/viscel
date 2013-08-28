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

class IndexPage(user: UserNode) extends HtmlPage {
	override def Title = "Index"

	def content = {
		val bookmarks = user.bookmarks.toStream
		val (unread, current) = bookmarks.map { bm => (bm.collection, bm.collection.name, bm.distanceToLast) }.partition { _._3 > 0 }
		val unreadTags = unread.sortBy { -_._3 }.map { case (id, name, unread) => link_node(id, s"$name ($unread)") }
		val currentTags = current.sortBy { _._2 }.map { case (id, name, unread) => link_node(id, s"$name") }

		body.id("index")(
			makeFieldset("Search", Seq(searchForm(""))).cls("info"),
			div.cls("navigation")(
				link_stop("stop")),
			makeFieldset("New Pages", unreadTags).cls("group"),
			makeFieldset("Bookmarks", currentTags).cls("group"))
	}

	def makeFieldset(name: String, entries: Seq[STag]) = fieldset(legend(name), div(entries.flatMap { e => Seq[STag](e, <br/>) }))
}

object IndexPage {
	def apply(user: UserNode) = new IndexPage(user).response
}
