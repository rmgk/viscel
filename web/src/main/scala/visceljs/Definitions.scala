package visceljs

import viscel.shared.{Blob, Description}
import visceljs.Actions.{gotoFront, gotoIndex, gotoView, onLeftClick}

import scala.scalajs.js.URIUtils.encodeURIComponent
import scalatags.JsDom.all.{Frag, SeqFrag, Tag, a, button, cls, href, stringAttr}


object Definitions {


  def path_main = "#"
	def path_css = "css"
	def path_asset(data: Data) = s"#${encodeURIComponent(data.id)}/${data.pos + 1}"
	def path_search = "s"
	def path_blob(blob: Blob) = s"blob/${blob.sha1}/${blob.mime}"
	def path_front(nar: Description) = s"#${encodeURIComponent(nar.id)}"
	def path_stop = "stop"
	def path_tools = "tools"

	val class_post = cls := "post"
	val class_extern = cls := "extern"
	val class_placeholder = cls := "placeholder"
	val class_dead = cls := "dead"
	val class_preview = cls := "preview"
	val class_chapters = cls := "chapters"
  val class_button = cls:= "pure-button"
  val class_button_disabled = cls:= "pure-button pure-button-disabled"
  val class_button_group = cls := "pure-button-group"



  def link_index(ts: Frag*): Tag = button(class_button, onLeftClick(gotoIndex()))(ts)
	def link_tools(ts: Frag*): Tag = a(href := path_tools)(ts)

  def link_asset(data: Data): Tag = a.apply(onLeftClick(gotoView(data)), href := path_asset(data))
  def button_asset(data: Data,  onleft: => Unit): Tag = {
    if (data.gallery.isEnd) button(class_button_disabled)
    else Make.lcButton(onleft)
  }

  def link_front(nar: Description, ts: Frag*): Tag = a(onLeftClick(gotoFront(nar)), href := path_front(nar))(ts)
  def button_front(nar: Description, ts: Frag*): Tag = Make.lcButton(gotoFront(nar))(ts)


}
