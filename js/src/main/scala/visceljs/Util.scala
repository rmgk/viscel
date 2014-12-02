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


object Util {

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
	val class_submit = cls := "submit"
	val class_content = cls := "content"
	val class_pages = cls := "pages"
	val class_extern = cls := "extern"
	val class_link = cls := "fakelink"

	def link_index(ts: Frag*): Tag = span(class_link)(onclick := (() => pushIndex()))(ts)
	def link_stop(ts: Frag*): Tag = a(href := path_stop)(ts)
	def link_asset(nar: Narration, gallery: Gallery[Asset], ts: Frag*): Tag =
		if (gallery.isEnd) span(ts)
		else span(class_link)(ts)(onclick := (() => pushView(gallery, nar)))
	def link_front(nar: Narration, ts: Frag*): Tag = span(class_link)(ts)(onclick := (() => pushFront(nar)))


	def pushFront(nar: Narration): Unit = {
		dom.history.pushState("", "front", path_front(nar))
		renderFront(nar)
	}
	def renderFront(nar: Narration): Unit = {
		for (bm <- Viscel.bookmarks; fullNarration <- Viscel.completeNarration(nar))
			Viscel.setBody(FrontPage.gen(bm.getOrElse(nar.id, 0), fullNarration))
	}

	def pushView(gallery: Gallery[Asset], nar: Narration): Unit = {
		dom.history.pushState("", "view", path_asset(nar, gallery))
		renderView(gallery, nar)
	}
	def renderView(gallery: Gallery[Asset], nar: Narration): Unit = {
		Viscel.setBody(ViewPage.gen(gallery, nar))
	}

	def pushIndex() = {
		dom.history.pushState("", "main", path_main)
		renderIndex()
	}
	def renderIndex() = {
		for (bm <- Viscel.bookmarks; nar <- Viscel.narrations) { Viscel.setBody(IndexPage.gen(bm, nar)) }
	}

	def dispatchPath(path: String): Unit = {
		val paths = List(path.split("/"): _*)
		paths match {
			case Nil | "" :: Nil =>
				Util.renderIndex()
			case id :: Nil =>
				for (nar <- Viscel.narrations) {
					Util.renderFront(nar(id))
				}
			case id :: posS :: Nil =>
				val pos = Integer.parseInt(posS)
				for {
					nars <- Viscel.narrations
					nar = nars(id)
					fullNarration <- Viscel.completeNarration(nar)
				} {
					Util.renderView(fullNarration.narrates.first.next(pos - 1), nar)
				}
			case _ => Util.renderIndex()
		}
	}


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
		form(inputField, action := "")(onsubmit := { () => filtered.headOption.foreach(pushFront); false })(id := "searchform")
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
