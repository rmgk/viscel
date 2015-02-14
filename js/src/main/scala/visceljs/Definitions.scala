package visceljs

import viscel.shared.Description
import visceljs.Actions.{gotoFront, gotoIndex, gotoView, onLeftClick}

import scala.Predef.$conforms
import scalatags.JsDom.all.{Frag, SeqFrag, Tag, a, cls, href, stringAttr}


object Definitions {


	def path_main = "#"
	def path_css = "css"
	def path_asset(data: Data) = s"#${ data.id }/${ data.pos + 1 }"
	def path_search = "s"
	def path_blob(blob: String) = if (blob.contains("/")) blob else s"blob/${ blob }"
	def path_front(nar: Description) = s"#${ nar.id }"
	def path_stop = "stop"

	val class_post = cls := "post"
	val class_extern = cls := "extern"
	val class_placeholder = cls := "placeholder"
	val class_dead = cls := "dead"
	val class_preview = cls := "preview"
	val class_chapters = cls := "chapters"


	def link_index(ts: Frag*): Tag = a(onLeftClick(gotoIndex()), href := path_main)(ts)
	def link_stop(ts: Frag*): Tag = a(href := path_stop)(ts)
	def link_asset(data: Data): Tag =
		if (data.gallery.isEnd) a(class_dead)
		else a.apply(onLeftClick(gotoView(data)), href := path_asset(data))
	def link_front(nar: Description, ts: Frag*): Tag = a(onLeftClick(gotoFront(nar)), href := path_front(nar))(ts)


}
