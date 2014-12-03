package visceljs

import org.scalajs.dom
import org.scalajs.dom.{HTMLInputElement, HTMLElement}
import viscel.shared.Gallery
import viscel.shared.Story.{Asset, Narration}
import visceljs.render.{View, Index, Front}
import visceljs.Definitions._
import visceljs.Actions._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow

import scala.Predef.{$conforms, ArrowAssoc}
import scalatags.JsDom.all._
import scalatags.JsDom.attrs.style
import scalatags.JsDom.tags2.{nav, aside}

object Render {

	
	def formPostBookmark(nar: Narration, pos: Int, ts: Frag*): HtmlTag = a(class_post)(ts)(onclick := { () => Viscel.postBookmark(nar, pos) })

	def searchArea(narrations: List[Narration]): HtmlTag = aside {
		val results = ol.render
		var filtered = Seq[Narration]()
		lazy val inputField: HTMLInputElement = input(`type` := "textfield", autofocus := true, onkeyup := { () =>
			results.innerHTML = ""
			val query = inputField.value.toString.toLowerCase
			if (!query.isEmpty) {
				filtered = SearchUtil.search(query, narrations.map(n => n.name -> n))
				filtered.foreach(nar => results.appendChild(li(link_front(nar, nar.name)).render))
			}
		}).render

		form(fieldset(legend("Search"), inputField, results), action := "", onsubmit := { () => filtered.headOption.foreach(gotoFront); false })
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
