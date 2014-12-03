package visceljs

import viscel.shared.Gallery
import viscel.shared.Story.{Asset, Narration}
import visceljs.Actions.{gotoFront, gotoIndex, gotoView}

import scala.Predef.$conforms
import scalatags.JsDom.all.{Frag, SeqFrag, Tag, a, bindJsAnyLike, cls, href, onclick, stringAttr}


object Definitions {


	def path_main = "/"
	def path_css = "/css"
	def path_asset(nr: Narration, gallery: Gallery[Asset]) = s"/#${ nr.id }/${ gallery.pos + 1 }"
	def path_search = "/s"
	def path_blob(blob: String) = s"/blob/${ blob }"
	def path_front(nar: Narration) = s"/#${ nar.id }"
	def path_stop = "/stop"
	def path_scripts = "/viscel.js"

	val class_main = cls := "main"
	val class_navigation = cls := "navigation"
	val class_side = cls := "side"
	val class_element = cls := "element"
	val class_info = cls := "info"
	val class_group = cls := "group"
	val class_post = cls := "post"
	val class_content = cls := "content"
	val class_pages = cls := "pages"
	val class_extern = cls := "extern"
	val class_link = cls := "fakelink"
	val class_placeholder = cls := "placeholder"
	val class_dead = cls := "dead"
	val class_preview = cls := "preview"
	val class_chapters = cls := "chapters"


	def link_index(ts: Frag*): Tag = a(onclick := (() => gotoIndex()))(ts)
	def link_stop(ts: Frag*): Tag = a(href := path_stop)(ts)
	def link_asset(nar: Narration, gallery: Gallery[Asset], ts: Frag*): Tag =
		if (gallery.isEnd) a(class_dead)(ts)
		else a(ts, onclick := (() => gotoView(gallery, nar)))
	def link_front(nar: Narration, ts: Frag*): Tag = a(ts)(onclick := (() => gotoFront(nar)))


}
