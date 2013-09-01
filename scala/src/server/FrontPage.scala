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

class FrontPage(user: UserNode, collection: CollectionNode) extends HtmlPage with MaskLocation with JavascriptNavigation {
	override def Title = collection.name
	override def bodyId = "front"
	override def maskLocation = path_front(collection.id)

	val bm = user.getBookmark(collection)
	val bm1 = bm.flatMap { _.prev }
	val bm2 = bm1.flatMap { _.prev }

	def bmRemoveForm(bm: ElementNode) = form_post(path_nid(collection.nid),
		input.ctype("hidden").name("remove_bookmark").value(collection.nid),
		input.ctype("submit").name("submit").value("remove").cls("submit"))

	def mainPart = div.cls("info")(
		make_table(
			"id" -> collection.id,
			"name" -> collection.name,
			"chapter" -> collection.size.toString,
			"pages" -> collection.totalSize.toString))
	def navigation = Seq[STag](
		link_main("index"),
		" – ",
		link_node(collection.first, "first"),
		" chapter ",
		link_node(collection.last, "last"),
		" – ",
		link_node(collection.first.flatMap { _.first }, "first"),
		" page ",
		link_node(collection.last.flatMap { _.last }, "last"),
		" – ",
		bm.map { bmRemoveForm(_) }.getOrElse("remove"))
	def sidePart = div.cls("content")(Seq(
		bm2.map { e => link_node(Some(e), enodeToImg(e)) },
		bm1.map { e => link_node(Some(e), enodeToImg(e)) },
		bm.map { e => link_node(Some(e), enodeToImg(e)) }).flatten[STag])

	override def navPrev = bm2.orElse(bm1).orElse(collection.first).map { en => path_nid(en.nid) }
	override def navNext = bm.orElse(collection.last).map { en => path_nid(en.nid) }
	override def navUp = Some(path_main)
}

object FrontPage {
	def apply(user: UserNode, collection: CollectionNode) = new FrontPage(user, collection).response
}
