package viscel.server

import com.typesafe.scalalogging.slf4j.Logging
import scala.xml.Elem
import scala.xml.Node
import scala.xml.NodeSeq
import scalatags._
import spray.http.{ HttpResponse, HttpEntity, MediaTypes, ContentType, HttpCharsets }
import viscel.store.ChapteredCollectionNode
import viscel.store.ElementNode
import viscel.store.Neo
import viscel.store.UserNode
import viscel.store.ViscelNode
import viscel.store.{ Util => StoreUtil }
import viscel.time

class ChapteredFrontPage(user: UserNode, collection: ChapteredCollectionNode) extends HtmlPage with JavascriptNavigation with MaskLocation {
	override def Title = collection.name
	override def maskLocation = path_front(collection.id)

	val bm = user.getBookmark(collection)
	val bm1 = bm.flatMap { _.prev }
	val bm2 = bm1.flatMap { _.prev }

	def bmRemoveForm(bm: ElementNode) = form_post(path_front(collection.id),
		input.ctype("submit").name("submit").value("remove").cls("submit"))

	def content = {
		body.id("front")(
			div.cls("info")(
				table(tbody(
					tr(td("id"), td(collection.id)),
					tr(td("name"), td(collection.name)),
					tr(td("size"), td(collection.size.toString))))),
			div.cls("navigation")(
				link_main("index"),
				" – ",
				link_node(collection.first, "first"),
				" ",
				link_node(bm, "bookmark"),
				" ",
				link_node(collection.last, "last"),
				" – ",
				bm.map { bmRemoveForm(_) }.getOrElse("remove")),
			div.cls("content")(Seq(
				bm2.map { e => link_node(Some(e), enodeToImg(e)) },
				bm1.map { e => link_node(Some(e), enodeToImg(e)) },
				bm.map { e => link_node(Some(e), enodeToImg(e)) }).flatten[STag]))
	}

	override def navPrev = bm2.orElse(bm1).orElse(collection.first).map { en => path_nid(en.nid) }
	override def navNext = bm.orElse(collection.last).map { en => path_nid(en.nid) }
	override def navUp = Some(path_main)
}

object ChapteredFrontPage {
	def apply(user: UserNode, collection: ChapteredCollectionNode) = new ChapteredFrontPage(user, collection).response
}
