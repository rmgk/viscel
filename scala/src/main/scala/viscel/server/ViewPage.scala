package viscel.server

import spray.http.HttpResponse
import viscel.store.{ElementNode, UserNode}

import scalatags._
import scalatags.all._

class ViewPage(user: UserNode, enode: ElementNode) extends HtmlPage with MetaNavigation with MaskLocation {
	lazy val collection = enode.collection
	lazy val pos = enode.position

	override def Title = s"$pos – ${ enode.chapter.name } – ${ collection.name }"
	override def bodyId = "view"

	override def maskLocation = path_view(collection, pos)

	override def navPrev = enode.prev.map { en => path_nid(en) }
	override def navNext = enode.next.map { en => path_nid(en) }
	override def navUp = Some(path_nid(collection))

	override def mainPart = div(class_content)(link_node(enode.next, enodeToImg(enode))) :: Nil
	override def sidePart = "" :: Nil

	override def navigation = Seq[Node](
		link_node(enode.prev, "prev"),
		" ",
		link_node(collection, "front"),
		" ",
		form_post(path_nid(collection),
			input(`type` := "hidden", name := "bookmark", value := enode.nid.toString),
			input(`type` := "submit", name := "submit", value := "pause", class_submit)),
		" ",
		a(href := enode.origin)(class_extern)("site"),
		" ",
		link_node(enode.next, "next"))
}

object ViewPage {
	def apply(user: UserNode, enode: ElementNode): HttpResponse = new ViewPage(user, enode).response
}
