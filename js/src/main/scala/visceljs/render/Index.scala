package visceljs.render

import org.scalajs.dom.html
import rescala.engines.Engines.default
import rescala.engines.Engines.default.{Signal, Var}
import rescala.reactives.Signals
import rescalatags._
import viscel.shared.Description
import visceljs.Actions._
import visceljs.Definitions.{link_front, link_tools}
import visceljs.{Body, Make, SearchUtil, Viscel}

import scala.scalajs.js
import scalatags.JsDom.all._
import scalatags.JsDom.implicits.stringFrag
import scalatags.JsDom.tags2._

object Index {

	var inputQueryString = ""

	def gen(): Body = {

		val fragS: Signal[Tag] = rescala.reactives.Signals.lift(Viscel.bookmarks, Viscel.descriptions) { (bookmarks, descriptions) =>

			val bookmarkedNarrations: List[(Description, Int, Int)] =
				bookmarks.toList.flatMap { case (id, pos) =>
					descriptions.get(id).map { nr =>
						(nr, pos, nr.size - pos)
					}
				}

			val (hasNewPages, isCurrent) = bookmarkedNarrations.partition(_._3 > 15)
			val available = descriptions.values.toList.filter(d => !bookmarks.contains(d.id)).map(n => (n, 0, n.size))

			val inputQuery = Var(inputQueryString)
			inputQuery.observe(inputQueryString = _)
			val inputField = input(value := inputQueryString, `type` := "textfield", tabindex := "1", onkeyup := ({ (inp: html.Input) =>
				inputQuery.set(inp.value.toString.toLowerCase)
			}: js.ThisFunction))

			val filteredHasNewPages = inputQuery.map { query =>
				if (query.isEmpty) hasNewPages.sortBy(-_._3)
				else SearchUtil.search(query, hasNewPages.map(n => n._1.name -> n))
			}

			val filteredIsCurrent = inputQuery.map { query =>
				if (query.isEmpty) isCurrent.sortBy(_._1.name)
				else SearchUtil.search(query, isCurrent.map(n => n._1.name -> n))
			}

			val filteredAvailable = inputQuery.map { query => SearchUtil.search(query, available.map(n => n._1.name -> n)) }

			val firstSelected: Signal[Option[Description]] = Signals.lift(filteredHasNewPages, filteredIsCurrent, filteredAvailable) {
				(n, c, a) => n.headOption.orElse(c.headOption).orElse(a.headOption).map(_._1)
			}

			val searchForm = form(inputField, onsubmit := firstSelected map {sel =>  () => sel.foreach(gotoFront(_)); false })

			div(
					Make.navigation(Make.fullscreenToggle("TFS"), searchForm, link_tools("tools")),
					Make.group(s"Updates", filteredHasNewPages),
					Make.group(s"Bookmarks", filteredIsCurrent),
					Make.group(s"Available", filteredAvailable)): Tag
		}

		val rendered = fragS.asFragment

		Body(id = "index", title = "Viscel",
			frag = rendered)
	}


	def searchArea(narrations: List[Description], inputQuery: Signal[String]): HtmlTag = aside {

		val results = ol.render
		val filteredS: Signal[List[Description]] = inputQuery.map(query => SearchUtil.search(query, narrations.map(n => n.name -> n)))
		filteredS.observe { desc =>
			results.innerHTML = ""
			desc.foreach(nar => results.appendChild(li(link_front(nar, nar.name)).render))
		}

		fieldset(legend("Search"), results)
	}

}
