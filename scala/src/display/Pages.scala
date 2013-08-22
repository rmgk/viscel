package viscel.display

import scalatags._
import spray.http.{ HttpResponse, HttpEntity, MediaTypes, ContentType, HttpCharsets }
import viscel.store.ElementNode
import viscel.Element
import viscel.store.CollectionNode
import scala.xml.Elem
import scala.xml.Node
import scala.xml.NodeSeq
import com.typesafe.scalalogging.slf4j.Logging
import viscel.time

object MinimizeXML {
	def apply(node: Node): Node = node match {
		case el: Elem => el.copy(minimizeEmpty = true, child = el.child.map { apply(_) })
		case other => other
	}
}

trait HtmlPage extends Logging {

	def apply() = response

	def response = {
		val html = time("generate html") { fullHtml.toXML }
		val min = time("hack minimization") { MinimizeXML(html) }
		val body = time("generating string") { "<!DOCTYPE html>" + min.toString }

		HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`text/html`, HttpCharsets.`UTF-8`), body))
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
	def link_node(en: Option[ElementNode], ts: STag*): STag = en.map { n => a.href(path_eid(n.id))(ts) }.getOrElse(ts)
	// def link_node(en: Option[ElementNode], ts: STag*): STag = en.map{n => link_view(n.collection.id, n.position, ts)}.getOrElse(ts)

	def make_form(action: String, ts: STag*) = form.attr("method" -> "post", "enctype" -> MediaTypes.`application/x-www-form-urlencoded`.toString).action(action)(ts)

	def elemToImg(elem: Element) = img.src(path_blob(elem.blob)).cls("element").attr(Seq(
		elem.width.map("width" -> _),
		elem.height.map("height" -> _),
		elem.alt.map("alt" -> _),
		elem.title.map("title" -> _)).flatten: _*)

}

object IndexPage extends HtmlPage {
	override def Title = "Index"

	def content = {
		val cols = collectionList
		val (bookmarks, noBookmarks) = cols.view
			.map { c => (c, c.unread) }.partition {
				case (c, Some(unread)) => true
				case _ => false
			}
		val bmTags = bookmarks.map { case (c, Some(unread)) => c.id -> unread }
			.sortBy { -_._2 }
			.map { case (id, unread) => link_front(id, s"$id ($unread)") }
		val noBmTags = noBookmarks.map { _._1.id }.sorted.map { id => link_front(id, id) }
		body.id("index")(
			makeGroup("Bookmarked", bmTags),
			makeGroup("Rest", noBmTags))
	}
	def collectionList = time("get collection list") { CollectionNode.list }
	def makeGroup(name: String, entries: Seq[STag]) = fieldset.cls("group")(legend(name), entries.flatMap { e => Seq(e, br) })
}

class FrontPage(collection: CollectionNode) extends HtmlPage {
	override def Title = collection.id
	def bmRemoveForm(bm: ElementNode) = make_form(path_front(collection.id),
		input.ctype("submit").name("submit").value("remove").cls("submit"))
	def content = {
		val bm = collection.bookmark
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
	def apply(collection: CollectionNode) = new FrontPage(collection).response
}

class ViewPage(enode: ElementNode) extends HtmlPage {
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
		make_form(path_front(collection.id),
			input.ctype("hidden").name("bookmark").value(enode.position),
			input.ctype("submit").name("submit").value("pause").cls("submit")),
		" ",
		a.href(element.origin).cls("extern")("site"),
		" ",
		link_node(enode.next, "next"))
}

// <input type="hidden" name="bookmark" value="518"/>
// <input type="submit" name="submit" value="pause" class="submit"/>

object ViewPage {
	def apply(enode: ElementNode) = new ViewPage(enode).response
}
