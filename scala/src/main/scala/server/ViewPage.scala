package viscel.server

import scalatags._
import spray.http.HttpResponse
import viscel.store.ElementNode
import viscel.store.UserNode
import viscel.store.{ Util => StoreUtil }

class ViewPage(user: UserNode, enode: ElementNode) extends HtmlPage with MetaNavigation with MaskLocation {
	lazy val collection = enode.collection
	lazy val pos = enode.position

	override def Title = s"${pos} – ${enode.chapter.name} – ${collection.name}"
	override def bodyId = "view"

	override def maskLocation = path_view(collection.id, pos)

	override def navPrev = enode.prev.map { en => path_nid(en.nid) }
	override def navNext = enode.next.map { en => path_nid(en.nid) }
	override def navUp = Some(path_nid(collection.nid))

	def mainPart = div.cls("content")(link_node(enode.next, enodeToImg(enode)))
	def sidePart = "": STag

	def navigation = Seq[STag](
		link_node(enode.prev, "prev"),
		" ",
		link_node(collection, "front"),
		" ",
		form_post(path_nid(collection.nid),
			input.ctype("hidden").name("bookmark").value(enode.nid),
			input.ctype("submit").name("submit").value("pause").cls("submit")),
		" ",
		a.href(enode[String]("origin")).cls("extern")("site"),
		" ",
		link_node(enode.next, "next"))
}

object ViewPage {
	def apply(user: UserNode, enode: ElementNode): HttpResponse = new ViewPage(user, enode).response
}
