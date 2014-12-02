package visceljs

import org.scalajs.dom
import org.scalajs.dom.{HTMLInputElement, HTMLElement}
import viscel.shared.Gallery
import viscel.shared.Story.{Asset, Narration}
import visceljs.render.{View, Index, Front}
import visceljs.Definitions._
import visceljs.Navigation._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow

import scala.Predef.{$conforms, ArrowAssoc}
import scalatags.JsDom.all._
import scalatags.JsDom.attrs.style
import scalatags.JsDom.tags2.nav

object Render {



	def set_bookmark(nar: Narration, pos: Int, ts: Frag*): HtmlTag = span(class_link, class_submit)(ts)(onclick := { () =>
		Viscel.setBookmark(nar, pos)
	})



	def form_search(narrations: List[Narration], results: HTMLElement): HtmlTag = {
		var filtered = Seq[Narration]()
		lazy val inputField: HTMLInputElement = input(`type` := "textfield", autofocus := true, onkeyup := { () =>
			results.innerHTML = ""
			val query = inputField.value.toString.toLowerCase
			if (!query.isEmpty) {
				filtered = SearchUtil.search(query, narrations.map(n => n.name -> n))
				val mapped = filtered.map(a => link_front(a, a.name))
				results.appendChild(make_fieldset("", mapped)(class_group).render)
				()
			}
		}).render
		form(inputField, action := "")(onsubmit := { () => filtered.headOption.foreach(gotoFront); false })(id := "searchform")
	}

	def blobToImg(asset: Asset): Frag = {
		asset.blob.fold[HtmlTag](div(class_element)("placeholder"))(blob => img(src := path_blob(blob.sha1), class_element))
	}

	def make_table(entry: (String, Frag)*) = table(tbody(SeqNode(entry.map { case (k, v) => tr(td(k), td(v)) })))

	def make_fieldset(name: String, entries: Seq[Frag]) = fieldset(legend(name), div(entries.flatMap { e => List(e, br) }))

	def makeNavigation(navigation: List[Tag]): Tag =
		nav(class_navigation)(navigation.map(e =>
			e(style := s"width: ${50/navigation.size}%; padding: 0 ${25/navigation.size}%; display: inline-block")))
}
