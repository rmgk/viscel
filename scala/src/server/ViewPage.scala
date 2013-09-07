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
import viscel._

class ViewPage(user: UserNode, enode: ElementNode) extends HtmlPage with JavascriptNavigation with MaskLocation {
	val collection = enode.collection

	override def Title = s"${enode.position} – ${enode.chapter.name} – ${collection.name}"
	override def bodyId = "view"

	override def maskLocation = path_view(collection.id, enode.chapter.position, enode.position)

	override def navPrev = enode.prevView.map { en => path_nid(en.nid) }
	override def navNext = enode.nextView.map { en => path_nid(en.nid) }
	override def navUp = Some(path_nid(collection.nid))

	def mainPart = div.cls("content")(link_node(enode.nextView, enodeToImg(enode)))
	def sidePart = "": STag

	def navigation = Seq[STag](
		link_node(enode.prevView, "prev"),
		" ",
		link_node(collection, "front"),
		" ",
		form_post(path_nid(collection.nid),
			input.ctype("hidden").name("bookmark").value(enode.nid),
			input.ctype("submit").name("submit").value("pause").cls("submit")),
		" ",
		a.href(enode[String]("origin")).cls("extern")("site"),
		" ",
		link_node(enode.nextView, "next"))
}

object ViewPage {
	def apply(user: UserNode, enode: ElementNode): HttpResponse = new ViewPage(user, enode).response
}
