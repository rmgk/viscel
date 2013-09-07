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

trait MaskLocation extends HtmlPage {
	def maskLocation: String

	override def header: HtmlTag = super.header(script(s"window.history.replaceState('param one?','param two?','${maskLocation}')"))
}

trait HtmlPageUtils {

	def path_main = "/index"
	def path_css = "/css"
	def path_front(id: String) = s"/f/$id"
	def path_chapter(id: String) = s"/c/$id"
	def path_view(id: String, absPos: Int) = s"/v/$id/$absPos"
	def path_view(id: String, chapter: Int, pos: Int) = s"/v/$id/$chapter/$pos"
	def path_search = "/s";
	def path_blob(id: String) = s"/b/$id"
	def path_nid(id: Long) = s"/i/$id"
	def path_raw(id: Long) = s"/r/$id"
	def path_stop = "/stop"

	def link_main(ts: STag*) = a.href(path_main)(ts)
	def link_stop(ts: STag*) = a.href(path_stop)(ts)
	def link_front(id: String, ts: STag*) = a.href(path_front(id))(ts)
	def link_view(id: String, chapter: Int, pos: Int, ts: STag*) = a.href(path_view(id, chapter, pos))(ts)
	def link_node(vn: ViscelNode, ts: STag*): STag = a.href(path_nid(vn.nid))(ts)
	def link_node(vn: Option[ViscelNode], ts: STag*): STag = vn.map { link_node(_, ts: _*) }.getOrElse(ts)
	def link_raw(vn: ViscelNode, ts: STag*): STag = a.href(path_raw(vn.nid))(ts)
	// def link_node(en: Option[ElementNode], ts: STag*): STag = en.map{n => link_view(n.collection.id, n.position, ts)}.getOrElse(ts)

	def form_post(action: String, ts: STag*) = form.attr("method" -> "post", "enctype" -> MediaTypes.`application/x-www-form-urlencoded`.toString).action(action)(ts)
	def form_get(action: String, ts: STag*) = form.attr("method" -> "get", "enctype" -> MediaTypes.`application/x-www-form-urlencoded`.toString).action(action)(ts)

	def form_search(init: String) = form_get(path_search, input.ctype("textfield").name("q").value(init))

	def enodeToImg(en: ElementNode) = en.get[String]("blob").map { blob =>
		img.src(path_blob(blob)).cls("element")
			.attr { Seq("alt", "title", "width", "height").flatMap { k => en.get[Any](k).map { v => k -> v.toString } }: _* }
	}.getOrElse(div.cls("info")("Placeholder"))

	def make_table(entry: (String, STag)*) = table(tbody(entry.map {
		case (k, v) =>
			tr(td(k), td(v))
	}))

	def make_fieldset(name: String, entries: Seq[STag]) = fieldset(legend(name), div(entries.flatMap { e => Seq[STag](e, <br/>) }))

}

trait JavascriptNavigation extends HtmlPage {
	override def header = super.header(script(scala.xml.Unparsed(keyNavigation(up = navUp, down = navDown, prev = navPrev, next = navNext))))

	def navUp: Option[String] = None
	def navNext: Option[String] = None
	def navPrev: Option[String] = None
	def navDown: Option[String] = None

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
