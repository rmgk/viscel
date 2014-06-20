package viscel.server

import spray.http.HttpResponse
import viscel.store.{AssetNode, UserNode}

import scalatags.Text._
import scalatags.Text.all._

class ViewPage(user: UserNode, enode: AssetNode) extends HtmlPage with MetaNavigation with MaskLocation {
	lazy val collection = enode.collection
	lazy val pos = enode.position

	override def Title = s"$pos – ${ collection.name }"
	override def bodyId = "view"

	override def maskLocation = path_view(collection, pos)

	override def navPrev = enode.prevAsset.map { en => path_nid(en) }
	override def navNext = enode.nextAsset.map { en => path_nid(en) }
	override def navUp = Some(path_nid(collection))

	override def mainPart = div(class_content)(link_node(enode.nextAsset, enodeToImg(enode))) :: Nil
	override def sidePart = "" :: Nil

	override def navigation = Seq[Node](
		link_node(enode.prevAsset, "prev"),
		" ",
		link_node(collection, "front"),
		" ",
		form_post(path_nid(collection),
			input(`type` := "hidden", name := "bookmark", value := enode.nid.toString),
			input(`type` := "submit", name := "submit", value := "pause", class_submit)),
		" ",
		a(href := enode.origin.toString)(class_extern)("site"),
		" ",
		link_node(enode.nextAsset, "next"))
}

object ViewPage {
	def apply(user: UserNode, enode: AssetNode): HttpResponse = new ViewPage(user, enode).response
}
