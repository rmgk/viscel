import scala.Predef.conforms
import scala.Predef.augmentString
import scalatags.JsDom.Tag
import scalatags.JsDom.all._

object HtmlPageUtils {

	def path_main = "/index"
	def path_css = "/css"
//	def path_front(collection: Collection) = s"/f/${ collection.id }"
//	def path_view(collection: Collection, absPos: Int) = s"/v/${ collection.id }/$absPos"
	def path_search = "/s"
	def path_blob(blob: String) = s"/b/${ blob }"
//	def path_nid(vn: Coin) = s"/i/${ vn.nid }"
//	def path_raw(vn: Coin) = s"/r/${ vn.nid }"
//	def path_stop = "/stop"
//	def path_core(core: Narrator) = s"/f/${ core.id }"
	def path_scripts = "/viscel.js"

	val class_main = cls := "main"
	val class_navigation = cls := "navigation"
	val class_side = cls := "side"
	val class_element = cls := "element"
	val class_info = cls := "info"
	val class_group = cls := "group"
	val class_submit = cls := "submit"
	val class_content = cls := "content"
	val class_pages = cls := "pages"
	val class_extern = cls := "extern"

	def link_main(ts: Frag*) = a(href := path_main)(ts)
//	def link_stop(ts: Frag*) = a(href := path_stop)(ts)
//	//def link_front(collection: CollectionNode, ts: Frag*) = a(href := path_front(collection))(ts)
//	//def link_view(id: String, chapter: Int, pos: Int, ts: Frag*) = a.href(path_view(id, chapter, pos))(ts)
//	def link_node(vn: Coin, ts: Frag*): Frag = a(href := path_nid(vn))(ts)
//	def link_node(vn: Option[Coin], ts: Frag*): Frag = vn.map { link_node(_, ts: _*) }.getOrElse(span(ts: _*))
//	def link_raw(vn: Coin, ts: Frag*): Frag = a(href := path_raw(vn))(ts)
//	// def link_node(en: Option[ElementNode], ts: Frag*): Frag = en.map{n => link_view(n.collection.id, n.position, ts)}.getOrElse(ts)
//	def link_core(core: Narrator): Frag = a(href := path_core(core))(core.name)

	def form_post(formAction: String, ts: Frag*) = form(
		"method".attr := "post",
		"enctype".attr := "application/x-www-form-urlencoded",
		action := formAction)(ts)

	def form_get(formAction: String, ts: Frag*) = form(
		"method".attr := "get",
		"enctype".attr := "application/x-www-form-urlencoded",
		action := formAction)(ts)

	def form_search(init: String) = form_get(path_search, input(`type` := "textfield", name := "q", value := init))(id := "searchform")

	def blobToImg(blob: String) = {
		img(src := path_blob(blob), class_element)
	}

	def make_table(entry: (String, Frag)*) = table(tbody(SeqNode(entry.map {
		case (k, v) =>
			tr(td(k), td(v))
	})))

	def make_fieldset(name: String, entries: Seq[Frag]) = fieldset(legend(name), div(entries.flatMap { e => List(e, br) }))

}
