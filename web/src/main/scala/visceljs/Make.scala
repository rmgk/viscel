package visceljs

import org.scalajs.dom.html.UList
import rescala.Signal
import viscel.shared.{Blob, Description, SharedImage}
import visceljs.Definitions._

import scalatags.JsDom.all.{alt, _}
import scalatags.JsDom.attrs.{onclick, style}
import scalatags.JsDom.tags.a
import scalatags.JsDom.tags2.{nav, section}

object Make {

	def postBookmark(bm: Int, data: Data, handler: Data => Unit, ts: Frag*): HtmlTag = {
		if (data.bookmark != bm) {
			Make.lcButton{
				ViscelJS.postBookmark(data.description, bm)
				handler(data.copy(bookmark = bm))
			}(ts)
		}
		else {
      button(class_button_disabled, ts)
    }
	}

	def postForceHint(nar: Description, ts: Frag*): HtmlTag = lcButton(ViscelJS.hint(nar, force = true), class_button)(ts)

	def imageStyle(fitType: Int): Modifier = {
		def s(mw: Boolean = false, mh: Boolean = false, w: Boolean = false, h: Boolean = false) =
			s"max-height: ${if (mh) "100vh" else "none"}; max-width: ${if (mw) "100vw" else "none"}; height: ${if (h) "100vh" else "auto"}; width: ${if (w) "100vw" else "auto"}"
		style := (fitType % 8 match {
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

	def asset(asset: SharedImage, assetData: Data, addImageStyle: Modifier = ""): List[Modifier] = {
		asset.blob match {
			case None => List(class_placeholder, "placeholder")
			case Some(blob@Blob(_, "application/x-shockwave-flash")) =>
				`object`(
					`type` := "application/x-shockwave-flash",
					data := path_blob(blob),
					width := asset.data.getOrElse("width", ""),
					height := asset.data.getOrElse("height", "")) :: Nil
			case Some(blob@Blob(_, _)) =>
				img(src := path_blob(blob), title := asset.data.getOrElse("title", ""), alt := asset.data.getOrElse("alt", ""))(addImageStyle) :: Nil
		}
	}

	def fullscreenToggle(stuff: Frag*): HtmlTag = a(cls := "pure-button", onclick := (() => ViscelJS.toggleFullscreen()))(stuff)

  def lcButton(action: => Unit, m: Modifier*): HtmlTag = button(class_button, Actions.onLeftClick(action))(m:_*)

	def group(name: String, entries: Signal[Seq[(Description, Int, Int)]]): Tag = {
		val elements: UList = ul.render
		val rLegend = legend(name).render
		entries.observe { es =>
			elements.innerHTML = ""
			var cUnread = 0
			var cTotal = 0
			var cPos = 0
			es.foreach { case (desc, pos, unread) =>
				val e = link_front(desc, s"${if (desc.unknownNarrator) "\uD83D\uDCD5 " else ""}${desc.name}${if (unread == 0) "" else s" ($unread)"}")
				elements.appendChild(li(e).render)
				if (unread > 0) cUnread += unread
				cTotal += desc.size
				cPos += pos
			}
			rLegend.textContent = s"$name $cUnread ($cPos/$cTotal)"
		}

		section(fieldset(rLegend, elements))
	}

	def navigation(links: Tag*): HtmlTag =
		nav(class_button_group)(links)
}
