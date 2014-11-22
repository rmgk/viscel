package viscel.serverStaticPages.pages

import viscel.database.Ntx
import viscel.serverStaticPages.{HtmlPage, MaskLocation, MetaNavigation}
import viscel.store.User
import viscel.store.coin.Asset

import scalatags.Text.all._

class ViewPage(user: User, enode: Asset)(implicit ntx: Ntx) extends HtmlPage with MetaNavigation with MaskLocation {
	lazy val collection = enode.collection
	lazy val pos = enode.position

	override def Title = s"$pos – ${ collection.name }"
	override def bodyId = "view"

	override def maskLocation = path_view(collection, pos)

	override def navPrev = enode.prev.map { en => path_nid(en) }
	override def navNext = enode.next.map { en => path_nid(en) }
	override def navUp = Some(path_nid(collection))

	override def mainPart = div(class_content)(link_node(enode.next, enodeToImg(enode))) :: Nil
	override def sidePart = "" :: Nil

	override def navigation = Seq[Frag](
		link_node(enode.prev, "prev"),
		" ",
		link_node(collection, "front"),
		" ",
		form_post(path_nid(collection),
			input(`type` := "hidden", name := "collection", value := collection.id),
			input(`type` := "hidden", name := "bookmark", value := pos),
			input(`type` := "submit", name := "submit", value := "pause", class_submit)),
		" ",
		a(href := enode.origin.toString)(class_extern)("site"),
		" ",
		link_node(enode.next, "next"))
}
