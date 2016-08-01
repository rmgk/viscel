package visceljs

import org.scalajs.dom.html.UList
import rescala.engines.Engines.default
import rescala.engines.Engines.default.Signal
import viscel.shared.{Article, Description}
import visceljs.Definitions._

import scalatags.JsDom.all._
import scalatags.JsDom.attrs.{onclick, style}
import scalatags.JsDom.tags.a
import scalatags.JsDom.tags2.{nav, section}

object Make {

	def postBookmark(nar: Description, bm: Int, data: Data, handler: Data => Unit, ts: Frag*): HtmlTag = a(class_post)(ts)(onclick := { () =>
		Viscel.postBookmark(nar, bm)
		handler(data.copy(bookmark = bm))
	})

	def postForceHint(nar: Description, ts: Frag*): HtmlTag = a(class_post)(ts)(onclick := { () =>
		Viscel.hint(nar, force = true)
	})

	def imageStyle(data: Data): Modifier = {
		def s(mw: Boolean = false, mh: Boolean = false, w: Boolean = false, h: Boolean = false) =
			s"max-height: ${if (mh) "100vh" else "none"}; max-width: ${if (mw) "100vw" else "none"}; height: ${if (h) "100vh" else "auto"}; width: ${if (w) "100vw" else "auto"}"
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
		asset.blob match {
			case None => List(class_placeholder, "placeholder")
			case Some(blob) =>
				img(src := path_blob(blob), title := asset.data.getOrElse("title", ""), alt := asset.data.getOrElse("alt", ""))(imageStyle(data)) :: Nil
		}
	}

	def fullscreenToggle(stuff: Frag*): Tag = a(onclick := (() => Viscel.toggleFullscreen()))(stuff)

	def group(name: String, entries: Signal[Seq[(Description, Int, Int)]]): Tag = {
		val elements: UList = ul.render
		val rLegend = legend(name).render
		entries.observe { es =>
			elements.innerHTML = ""
			var cUnread = 0
			var cTotal = 0
			var cPos = 0
			es.foreach { case (nr, pos, unread) =>
				val e = link_front(nr, s"${nr.name}${if (unread == 0) "" else s" ($unread)"}")
				elements.appendChild(li(e).render)
				if (unread > 0) cUnread += unread
				cTotal += nr.size
				cPos += pos
			}
			rLegend.textContent = s"$name $cUnread ($cPos/$cTotal)"
		}

		section(fieldset(rLegend, elements))
	}

	def navigation(links: Tag*): Tag =
		nav(links)
}
