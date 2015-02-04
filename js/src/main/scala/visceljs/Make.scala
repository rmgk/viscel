package visceljs

import org.scalajs.dom.HTMLInputElement
import viscel.shared.Story.{Asset, Description}
import visceljs.Actions._
import visceljs.Definitions._

import scala.Predef.{$conforms, ArrowAssoc}
import scalatags.JsDom.all._
import scalatags.JsDom.attrs.{onclick, style}
import scalatags.JsDom.tags.{p, a}
import scalatags.JsDom.tags2.{article, aside, nav, section}

object Make {


	def postBookmark(nar: Description, pos: Int, ts: Frag*): HtmlTag = a(class_post)(ts)(onclick := { () => Viscel.postBookmark(nar, pos) })

	def searchArea(narrations: List[Description]): HtmlTag = aside {
		val results = ol.render
		var filtered = narrations
		filtered.sortBy(_.name).foreach(nar => results.appendChild(li(link_front(nar, nar.name)).render))
		lazy val inputField: HTMLInputElement = input(`type` := "textfield", onkeyup := { () =>
			results.innerHTML = ""
			val query = inputField.value.toString.toLowerCase
			filtered = SearchUtil.search(query, narrations.map(n => n.name -> n))
			filtered.foreach(nar => results.appendChild(li(link_front(nar, nar.name)).render))
		}).render

		form(fieldset(legend("Search"), inputField, results), action := "", onsubmit := { () => filtered.headOption.foreach(gotoFront); false })
	}

	def asset(asset: Asset): List[Modifier] = {
		asset.blob.fold[List[Modifier]](List(class_placeholder, "placeholder"))(blob => img(src := path_blob(blob.sha1)) :: Nil)
	}

	def fullscreenToggle(stuff: Frag*): Tag = a(onclick := (() => Viscel.toggleFullscreen()))(stuff)

	def group(name: String, entries: Seq[Frag]): Tag = section(fieldset(legend(name), ul(entries.map(li(_)))))

	def navigation(links: Tag*): Tag =
		nav(links.map(e =>
			e(style := s"text-align: center; width: ${ 100 / links.size }%; ")))
}
