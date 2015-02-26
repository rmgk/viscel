package visceljs

import org.scalajs.dom.html
import viscel.shared.{Article, Description}
import visceljs.Actions._
import visceljs.Definitions._

import scala.Predef.{$conforms, ArrowAssoc}
import scalatags.JsDom.all._
import scalatags.JsDom.attrs.{onclick, style}
import scalatags.JsDom.tags.a
import scalatags.JsDom.tags2.{aside, nav, section}

object Make {


	def updateBookmark(nar: Description, data: Data, ts: Frag*): HtmlTag = a(class_post)(ts)(onclick := { () =>
		val bm = data.gallery.pos + 1
		Viscel.postBookmark(nar, bm)
		gotoView(data.copy(bookmark = bm))
	})

	def removeBookmark(nar: Description, ts: Frag*): HtmlTag = a(class_post)(ts)(onclick := { () => Viscel.postBookmark(nar, 0) })

	def searchArea(narrations: List[Description]): HtmlTag = aside {
		val results = ol.render
		var filtered = narrations
		filtered.sortBy(_.name).foreach(nar => results.appendChild(li(link_front(nar, nar.name)).render))
		lazy val inputField: html.Input = input(`type` := "textfield", onkeyup := { () =>
			results.innerHTML = ""
			val query = inputField.value.toString.toLowerCase
			filtered = SearchUtil.search(query, narrations.map(n => n.name -> n))
			filtered.foreach(nar => results.appendChild(li(link_front(nar, nar.name)).render))
		}).render

		form(fieldset(legend("Search"), inputField, results), action := "", onsubmit := { () => filtered.headOption.foreach(gotoFront); false })
	}

	def imageStyle(data: Data): Modifier = {
		def s(mw: Boolean = false, mh: Boolean = false, w: Boolean = false, h: Boolean = false) =
			s"max-height: ${if(mh) "100vh" else "none" }; max-width: ${if(mw) "100vw" else "none" }; height: ${if(h) "100vh" else "auto" }; width: ${if(w) "100vw" else "auto" }"
		style := (data.fitType % 8 match {
			case 0 => ""
			case 1 => s()
			case 2 => s(mw = true)
			case 3 => s(mh = true)
			case 4 => s(mw = true, mh = true)
			case 5 => s(w = true)
			case 6 => s(h = true)
			case 7 => s(w = true, h = true)
		})
	}

	def asset(asset: Article, data: Data): List[Modifier] = {
		asset.blob.fold[List[Modifier]](List(class_placeholder, "placeholder"))(blob => img(src := path_blob(blob))(imageStyle(data)) :: Nil)
	}

	def fullscreenToggle(stuff: Frag*): Tag = a(onclick := (() => Viscel.toggleFullscreen()))(stuff)

	def group(name: String, entries: Seq[Frag]): Tag = section(fieldset(legend(name), ul(entries.map(li(_)))))

	def navigation(links: Tag*): Tag =
		nav(links)
}
