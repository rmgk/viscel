package visceljs

import viscel.shared.Gallery
import viscel.shared.Story.{Asset, Narration}

import scala.Predef.conforms
import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow
import scala.scalajs.js
import scala.scalajs.js.Dynamic.global
import scalatags.JsDom.all._


object Util {

	def path_main = "/"
	def path_css = "/css"
	def path_asset(nr: Narration, absPos: Int) = s"#${nr.id}/$absPos"
	def path_search = "/s"
	def path_blob(blob: String) = s"/blob/${ blob }"
	def path_front(nar: Narration) = s"#${nar.id}"
//	def path_raw(vn: Coin) = s"/r/${ vn.nid }"
	def path_stop = "/stop"
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
	def link_stop(ts: Frag*) = a(href := path_stop)(ts)
//	//def link_front(collection: CollectionNode, ts: Frag*) = a(href := path_front(collection))(ts)
	def link_asset(nar: Narration, gallery: Gallery[Asset], ts: Frag*) = a(href := path_asset(nar, gallery.pos + 1))(ts)(onclick := {() =>
		Viscel.setBody("view", ViewPage.gen(gallery, nar))
	})
	
	def link_front(nar: Narration, ts: Frag*): Frag = a(href := path_front(nar))(ts)(onclick := {() =>
		for (bm <- Viscel.bookmarks; fullNarration <- Viscel.completeNarration(nar))
			Viscel.setBody("front", FrontPage.genIndex(bm(nar.id), fullNarration))})
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

	def blobToImg(asset: Asset): HtmlTag = {
		img(src := path_blob(asset.blob.get.sha1), class_element)
	}

	def make_table(entry: (String, Frag)*) = table(tbody(SeqNode(entry.map {
		case (k, v) =>
			tr(td(k), td(v))
	})))

	def make_fieldset(name: String, entries: Seq[Frag]) = fieldset(legend(name), div(entries.flatMap { e => List(e, br) }))

	def keys(obj: js.Object): List[String] = {
		var res = List[String]()
		js.Object.keys(obj).map[Unit]{(s: String) => res ::= s}
		res.reverse
	}

	def parseInt(int: js.Any): Int = global.parseInt(int).asInstanceOf[Int]


}
