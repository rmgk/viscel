package visceljs

import org.scalajs.dom
import org.scalajs.dom.{HTMLElement, HTMLInputElement, KeyboardEvent}
import viscel.shared.Gallery
import viscel.shared.Story.{Asset, Narration}

import scala.Predef.{ArrowAssoc, $conforms}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow
import scala.scalajs.js
import scalatags.JsDom.TypedTag
import scalatags.JsDom.all._
import scalatags.JsDom.attrs.style
import scalatags.JsDom.tags2.nav
import Actions.{gotoFront, gotoView, gotoIndex}


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

	def link_index(ts: Frag*): Tag = a(onclick := (() => gotoIndex()))(ts)
	def link_stop(ts: Frag*): Tag = a(href := path_stop)(ts)
	def link_asset(nar: Narration, gallery: Gallery[Asset], ts: Frag*): Tag =
		if (gallery.isEnd) a(class_dead)(ts)
		else a(ts, onclick := (() => gotoView(gallery, nar)))
	def link_front(nar: Narration, ts: Frag*): Tag = a(ts)(onclick := (() => gotoFront(nar)))



}
