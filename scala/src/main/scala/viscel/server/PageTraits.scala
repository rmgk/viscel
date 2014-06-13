package viscel.server

import scalatags._
import scalatags.all._
import spray.http.MediaTypes
import viscel.store.ElementNode
import viscel.store.ViscelNode
import viscel.store.{ Util => StoreUtil }

trait MaskLocation extends HtmlPage {
	def maskLocation: String

	override def header: HtmlTag = super.header(script(s"window.history.replaceState('param one?','param two?','$maskLocation')"))
}

trait HtmlPageUtils {

	def path_main = "/index"
	def path_css = "/css"
	def path_front(id: String) = s"/f/$id"
	def path_chapter(id: String) = s"/c/$id"
	def path_view(id: String, absPos: Int) = s"/v/$id/$absPos"
	def path_view(id: String, chapter: Int, pos: Int) = s"/v/$id/$chapter/$pos"
	def path_search = "/s"
	def path_blob(id: String) = s"/b/$id"
	def path_nid(id: Long) = s"/i/$id"
	def path_raw(id: Long) = s"/r/$id"
	def path_stop = "/stop"

	val class_main = "main".cls
	val class_navigation = "navigation".cls
	val class_side = "side".cls
	val class_element = "element".cls
	val class_info = "info".cls
	val class_group = "group".cls
	val class_submit = "submit".cls
	val class_content = "content".cls
	val class_pages = "pages".cls
	val class_extern = "extern".cls

	def link_main(ts: Node*) = a(href := path_main)(ts)
	def link_stop(ts: Node*) = a(href := path_stop)(ts)
	//def link_front(id: String, ts: Node*) = a.href(path_front(id))(ts)
	//def link_view(id: String, chapter: Int, pos: Int, ts: Node*) = a.href(path_view(id, chapter, pos))(ts)
	def link_node(vn: ViscelNode, ts: Node*): Node = a(href := path_nid(vn.nid))(ts)
	def link_node(vn: Option[ViscelNode], ts: Node*): Node = vn.map { link_node(_, ts: _*) }.getOrElse(span(ts: _*))
	def link_raw(vn: ViscelNode, ts: Node*): Node = a(href := path_raw(vn.nid))(ts)
	// def link_node(en: Option[ElementNode], ts: Node*): Node = en.map{n => link_view(n.collection.id, n.position, ts)}.getOrElse(ts)

	def form_post(formAction: String, ts: Node*) = form("method".attr := "post", "enctype".attr := MediaTypes.`application/x-www-form-urlencoded`.toString, action := formAction)(ts)
	def form_get(formAction: String, ts: Node*) = form("method".attr := "get", "enctype".attr := MediaTypes.`application/x-www-form-urlencoded`.toString, action := formAction)(ts)

	def form_search(init: String) = form_get(path_search, input(`type` := "textfield", name := "q", value := init))(id := "searchform")

	def enodeToImg(en: ElementNode) = en.get[String]("blob").map { blob =>
		img(src := path_blob(blob), class_element) {
			Seq("alt", "title", "width", "height").flatMap { k => en.get[Any](k).map { v => k.attr := v.toString } }: _*
		}
	}.getOrElse(div(class_info)("Placeholder"))

	def make_table(entry: (String, Node)*) = table(tbody(entry.map {
		case (k, v) =>
			tr(td(k), td(v))
	}))

	def make_fieldset(name: String, entries: Seq[Node]) = fieldset(legend(name), div(entries.flatMap { e => Seq[Node](e, br) }))

}

trait MetaNavigation extends HtmlPage {
	override def header: HtmlTag = super.header(
		script(keyNavigation(up = navUp, down = navDown, prev = navPrev, next = navNext)),
		navNext.map { n => link(rel := "next", href := n) }.getOrElse(""),
		navPrev.map { p => link(rel := "prev", href := p) }.getOrElse(""))

	def navUp: Option[String] = None
	def navNext: Option[String] = None
	def navPrev: Option[String] = None
	def navDown: Option[String] = None

	def keypress(location: String, keyCodes: Int*) = s"""
			|if (${keyCodes.map { c => s"ev.keyCode === $c" }.mkString(" || ")}) {
			|	ev.preventDefault();
			|	document.location.pathname = "$location";
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
