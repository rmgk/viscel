package viscel.display

import com.typesafe.scalalogging.slf4j.Logging
import scala.xml.Elem
import scala.xml.Node
import scala.xml.NodeSeq
import scalatags._
import spray.http.{ HttpResponse, HttpEntity, MediaTypes, ContentType, HttpCharsets }
import viscel.Element
import viscel.store.CollectionNode
import viscel.store.Collections
import viscel.store.ElementNode
import viscel.store.UserNode
import viscel.time

object MinimizeXML {
	def apply(node: Node): Node = node match {
		case el: Elem => el.copy(minimizeEmpty = true, child = el.child.map { apply(_) })
		case other => other
	}
}

trait HtmlPage extends Logging {

	def response: HttpResponse = time("generate response") {
		HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`text/html`, HttpCharsets.`UTF-8`),
			"<!DOCTYPE html>" + MinimizeXML(fullHtml.toXML).toString))
	}

	def fullHtml = html(header, content)

	def header = head(stylesheet(path_css), title(Title))

	def Title = "Viscel"

	def content: STag

	def path_main = "/index"
	def path_css = "/css"
	def path_front(id: String) = s"/f/$id"
	def path_view(id: String, pos: Int) = s"/v/$id/$pos"
	def path_search = "/s";
	def path_blob(id: String) = s"/b/$id"
	def path_eid(id: Long) = s"/id/$id"

	def link_main(ts: STag*) = a.href(path_main)(ts)
	def link_front(id: String, ts: STag*) = a.href(path_front(id))(ts)
	def link_view(id: String, pos: Int, ts: STag*) = a.href(path_view(id, pos))(ts)
	def link_node(en: Option[ElementNode], ts: STag*): STag = en.map { n => a.href(path_eid(n.nid))(ts) }.getOrElse(ts)
	// def link_node(en: Option[ElementNode], ts: STag*): STag = en.map{n => link_view(n.collection.id, n.position, ts)}.getOrElse(ts)

	def form_post(action: String, ts: STag*) = form.attr("method" -> "post", "enctype" -> MediaTypes.`application/x-www-form-urlencoded`.toString).action(action)(ts)
	def form_get(action: String, ts: STag*) = form.attr("method" -> "get", "enctype" -> MediaTypes.`application/x-www-form-urlencoded`.toString).action(action)(ts)

	def searchForm(init: String) = form_get(path_search, input.ctype("textfield").name("q").value(init))

	def elemToImg(elem: Element) = img.src(path_blob(elem.blob)).cls("element").attr(Seq(
		elem.width.map("width" -> _),
		elem.height.map("height" -> _),
		elem.alt.map("alt" -> _),
		elem.title.map("title" -> _)).flatten: _*)

}

class IndexPage(user: UserNode) extends HtmlPage {
	override def Title = "Index"

	def content = {
		val bookmarks = user.bookmarks.toStream
		val (unread, current) = bookmarks.map { bm => bm.collection.id -> bm.distanceToLast }.partition { _._2 > 0 }
		val unreadTags = unread.sortBy { -_._2 }.map { case (id, unread) => link_front(id, s"$id ($unread)") }
		val currentTags = current.sortBy { _._1 }.map { case (id, unread) => link_front(id, s"$id") }

		body.id("index")(
			makeFieldset("Search", Seq(searchForm(""))).cls("info"),
			makeFieldset("New Pages", unreadTags).cls("group"),
			makeFieldset("Bookmarks", currentTags).cls("group"))
	}

	def makeFieldset(name: String, entries: Seq[STag]) = fieldset(legend(name), entries.flatMap { e => Seq(e, br) })
}

object IndexPage {
	def apply(user: UserNode) = new IndexPage(user).response
}

class SearchPage(user: UserNode, text: String) extends HtmlPage {
	override def Title = "Search"

	def content = {

		val containing = Collections.search(text)
			.map { cn => link_front(cn.id, cn.id) }

		body.id("search")(
			makeFieldset("Search", Seq(searchForm(text))).cls("info"),
			div.cls("navigation")(
				link_main("index")),
			makeFieldset(text, containing).cls("group"))
	}

	def searchForm = form_get(path_search, input.ctype("textfield").name("q"))

	def makeFieldset(name: String, entries: Seq[STag]) = fieldset(legend(name), entries.flatMap { e => Seq(e, br) })
}

object SearchPage {
	def apply(user: UserNode, text: String) = new SearchPage(user, text).response
}

class FrontPage(user: UserNode, collection: CollectionNode) extends HtmlPage {
	override def Title = collection.id
	def bmRemoveForm(bm: ElementNode) = form_post(path_front(collection.id),
		input.ctype("submit").name("submit").value("remove").cls("submit"))
	def content = {
		val bm = user.getBookmark(collection)
		val bm1 = bm.flatMap { _.prev }
		val bm2 = bm1.flatMap { _.prev }
		body.id("front")(
			div.cls("info")(
				table(tbody(tr(td("id"), td(collection.id)),
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
				bm2.map { e => link_node(Some(e), elemToImg(e.toElement)) },
				bm1.map { e => link_node(Some(e), elemToImg(e.toElement)) },
				bm.map { e => link_node(Some(e), elemToImg(e.toElement)) }).flatten[STag]))
	}
}

object FrontPage {
	def apply(user: UserNode, collection: CollectionNode) = new FrontPage(user, collection).response
}

class ViewPage(user: UserNode, enode: ElementNode) extends HtmlPage {
	val element = enode.toElement
	val collection = enode.collection

	override def Title = s"${enode.position} – ${collection.id}"

	def content = body.id("view")(
		div.cls("content")(
			link_node(enode.next, elemToImg(element))),
		div.cls("navigation")(navigation.toSeq))

	def navigation = Seq[STag](
		link_node(enode.prev, "prev"),
		" ",
		link_front(collection.id, "front"),
		" ",
		form_post(path_front(collection.id),
			input.ctype("hidden").name("bookmark").value(enode.nid),
			input.ctype("submit").name("submit").value("pause").cls("submit")),
		" ",
		a.href(element.origin).cls("extern")("site"),
		" ",
		link_node(enode.next, "next"))
}

// <input type="hidden" name="bookmark" value="518"/>
// <input type="submit" name="submit" value="pause" class="submit"/>

object ViewPage {
	def apply(user: UserNode, enode: ElementNode): HttpResponse = new ViewPage(user, enode).response
}
