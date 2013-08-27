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

object MinimizeXML {
	def apply(node: Node): Node = node match {
		case el: Elem => el.copy(minimizeEmpty = true, child = el.child.map { apply(_) })
		case other => other
	}
}

object PageDispatcher {
	def apply(user: UserNode, vn: ViscelNode) = vn match {
		//case n: ChapterNode =>
		case n: CollectionNode => FrontPage(user, n)
		// case n: ChapteredCollectionNode =>
		case n: ElementNode => ViewPage(user, n)
		//case n: UserNode =>
	}
}

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

class SearchPage(user: UserNode, text: String) extends HtmlPage {
	override def Title = "Search"

	def content = {
		val containing = StoreUtil.search(text)
			.map { cn => link_node(cn, cn.name) }

		body.id("search")(
			makeFieldset("Search", Seq(searchForm(text))).cls("info"),
			div.cls("navigation")(
				link_main("index")),
			makeFieldset(text, containing).cls("group"))
	}

	def searchForm = form_get(path_search, input.ctype("textfield").name("q"))

	def makeFieldset(name: String, entries: Seq[STag]) = fieldset(legend(name), entries.flatMap { e => Seq[STag](e, <br/>) })
}

object SearchPage {
	def apply(user: UserNode, text: String) = new SearchPage(user, text).response
}

class FrontPage(user: UserNode, collection: CollectionNode) extends HtmlPage {
	override def Title = collection.name
	override def maskLocation = Some(path_front(collection.id))

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

object FrontPage {
	def apply(user: UserNode, collection: CollectionNode) = new FrontPage(user, collection).response
}

class ViewPage(user: UserNode, enode: ElementNode) extends HtmlPage {
	val collection = enode.collection
	val cid = collection.id

	override def Title = s"${enode.position} – ${collection.name}"

	override def maskLocation = Some(path_view(cid, enode.position))

	override def navPrev = enode.prev.map { en => path_nid(en.nid) }
	override def navNext = enode.next.map { en => path_nid(en.nid) }
	override def navUp = Some(path_nid(collection.nid))

	def content = body.id("view")(
		div.cls("content")(
			link_node(enode.next, enodeToImg(enode))),
		div.cls("navigation")(navigation.toSeq))

	def navigation = Seq[STag](
		link_node(enode.prev, "prev"),
		" ",
		link_node(collection, "front"),
		" ",
		form_post(path_front(cid),
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

trait HtmlPage extends Logging {

	def response: HttpResponse = time(s"create response $Title") {
		HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`text/html`, HttpCharsets.`UTF-8`),
			"<!DOCTYPE html>" + fullHtml.toXML.toString))
	}

	def fullHtml = html(header, content)

	def header = head(
		stylesheet(path_css),
		title(Title),
		maskLocation.map { loc => script(s"window.history.replaceState('param one?','param two?','${loc}')") }.toSeq,
		script(scala.xml.Unparsed(keyNavigation(up = navUp, down = navDown, prev = navPrev, next = navNext))))

	def navUp: Option[String] = None
	def navNext: Option[String] = None
	def navPrev: Option[String] = None
	def navDown: Option[String] = None

	def maskLocation: Option[String] = None

	def Title = "Viscel"

	def content: STag

	def path_main = "/index"
	def path_css = "/css"
	def path_front(id: String) = s"/f/$id"
	def path_view(id: String, pos: Int) = s"/v/$id/$pos"
	def path_search = "/s";
	def path_blob(id: String) = s"/b/$id"
	def path_nid(id: Long) = s"/id/$id"
	def path_stop = "/stop"

	def link_main(ts: STag*) = a.href(path_main)(ts)
	def link_stop(ts: STag*) = a.href(path_stop)(ts)
	def link_front(id: String, ts: STag*) = a.href(path_front(id))(ts)
	def link_view(id: String, pos: Int, ts: STag*) = a.href(path_view(id, pos))(ts)
	def link_node(vn: ViscelNode, ts: STag*): STag = a.href(path_nid(vn.nid))(ts)
	def link_node(vn: Option[ViscelNode], ts: STag*): STag = vn.map { link_node(_, ts: _*) }.getOrElse(ts)
	// def link_node(en: Option[ElementNode], ts: STag*): STag = en.map{n => link_view(n.collection.id, n.position, ts)}.getOrElse(ts)

	def form_post(action: String, ts: STag*) = form.attr("method" -> "post", "enctype" -> MediaTypes.`application/x-www-form-urlencoded`.toString).action(action)(ts)
	def form_get(action: String, ts: STag*) = form.attr("method" -> "get", "enctype" -> MediaTypes.`application/x-www-form-urlencoded`.toString).action(action)(ts)

	def searchForm(init: String) = form_get(path_search, input.ctype("textfield").name("q").value(init))

	def enodeToImg(en: ElementNode) = img.src(path_blob(en[String]("blob"))).cls("element")
		.attr { Seq("width", "height").flatMap { k => en.get[Int](k).map { k -> _ } } ++ Seq("alt", "title").flatMap { k => en.get[String](k).map { k -> _ } }: _* }

	def keypress(location: String, keyCodes: Int*) = s"""
			|if (${keyCodes.map { c => s"ev.keyCode == $c" }.mkString(" || ")}) {
			|	ev.preventDefault();
			|	document.location.pathname = "${location}";
			|	return false;
			|}
			""".stripMargin
	def keyNavigation(prev: Option[String] = None, next: Option[String] = None, up: Option[String] = None, down: Option[String] = None) = s"""
			|document.onkeydown = function(ev) {
			|	if (!ev.ctrlKey && !ev.altKey) {
			|${prev.map { loc => keypress(loc, 37, 65, 188) }.getOrElse("")}
			|${next.map { loc => keypress(loc, 39, 68, 190) }.getOrElse("")}
			|${up.map { loc => keypress(loc, 13, 87, 77) }.getOrElse("")}
			|${down.map { loc => keypress(loc, 40, 83, 66, 78) }.getOrElse("")}
			| }
			|}
			""".stripMargin

}
