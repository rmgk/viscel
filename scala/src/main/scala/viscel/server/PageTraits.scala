package viscel.server

import spray.http.MediaTypes
import viscel.core.Core
import viscel.store.{ rel => _, _}

import scalatags._
import scalatags.all._

trait MaskLocation extends HtmlPage {
	def maskLocation: String

	override def header: HtmlTag = super.header(script(s"window.history.replaceState('param one?','param two?','$maskLocation')"))
}

trait HtmlPageUtils {

	def path_main = "/index"
	def path_css = "/css"
	def path_front(collection: CollectionNode) = s"/f/${ collection.id }"
	def path_view(collection: CollectionNode, absPos: Int) = s"/v/${ collection.id }/$absPos"
	def path_search = "/s"
	def path_blob(blob: BlobNode) = s"/b/${ blob.nid }"
	def path_nid(vn: ViscelNode) = s"/i/${ vn.nid }"
	def path_raw(vn: ViscelNode) = s"/r/${ vn.nid }"
	def path_stop = "/stop"
	def path_core(core: Core) = s"/core/${core.id}"

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
	def link_node(vn: ViscelNode, ts: Node*): Node = a(href := path_nid(vn))(ts)
	def link_node(vn: Option[ViscelNode], ts: Node*): Node = vn.map { link_node(_, ts: _*) }.getOrElse(span(ts: _*))
	def link_raw(vn: ViscelNode, ts: Node*): Node = a(href := path_raw(vn))(ts)
	// def link_node(en: Option[ElementNode], ts: Node*): Node = en.map{n => link_view(n.collection.id, n.position, ts)}.getOrElse(ts)
	def link_core(core: Core): Node = a(href := path_core(core))(core.name)

	def form_post(formAction: String, ts: Node*) = form("method".attr := "post", "enctype".attr := MediaTypes.`application/x-www-form-urlencoded`.toString, action := formAction)(ts)
	def form_get(formAction: String, ts: Node*) = form("method".attr := "get", "enctype".attr := MediaTypes.`application/x-www-form-urlencoded`.toString, action := formAction)(ts)

	def form_search(init: String) = form_get(path_search, input(`type` := "textfield", name := "q", value := init))(id := "searchform")

	def enodeToImg(en: AssetNode) = en.blob.fold(ifEmpty = div(class_info)("Placeholder")) { blob =>
		img(src := path_blob(blob), class_element) {
			Seq("alt", "title", "width", "height").flatMap { k => en.self.get[Any](k).map { v => k.attr := v.toString } }: _*
		}
	}

	def make_table(entry: (String, Node)*) = table(tbody(entry.map {
		case (k, v) =>
			tr(td(k), td(v))
	}))

	def make_fieldset(name: String, entries: Seq[Node]) = fieldset(legend(name), div(entries.flatMap { e => List(e, br) }))

}

trait MetaNavigation extends HtmlPage {
	override def header: HtmlTag = super.header(
		script(RawNode(keyNavigation(up = navUp, down = navDown, prev = navPrev, next = navNext))),
		navNext.map { n => link(rel := "next", href := n) }.getOrElse(""),
		navPrev.map { p => link(rel := "prev", href := p) }.getOrElse(""))

	def navUp: Option[String] = None
	def navNext: Option[String] = None
	def navPrev: Option[String] = None
	def navDown: Option[String] = None

	def keypress(location: String, keyCodes: Int*) = s"""
			|if (${ keyCodes.map { c => s"ev.keyCode === $c" }.mkString(" || ") }) {
			|	ev.preventDefault();
			|	document.location.pathname = "$location";
			|	return false;
			|}
			""".stripMargin

	def keyNavigation(prev: Option[String] = None, next: Option[String] = None, up: Option[String] = None, down: Option[String] = None) = s"""
			|document.onkeydown = function(ev) {
			|	if (!ev.ctrlKey && !ev.altKey) {
			|${ prev.fold("") { loc => keypress(loc, 37, 65, 188) } }
			|${ next.fold("") { loc => keypress(loc, 39, 68, 190) } }
			|${ up.fold("") { loc => keypress(loc, 13, 87, 77) } }
			|${ down.fold("") { loc => keypress(loc, 40, 83, 66, 78) } }
			| }
			|}
			""".stripMargin

}
