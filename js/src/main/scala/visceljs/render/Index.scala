package visceljs.render

import org.scalajs.dom.html
import rescala.turns.Engines.synchron
import rescala.turns.Engines.synchron.{Signal, Var}
import viscel.shared.Description
import visceljs.Actions._
import visceljs.Definitions.{link_front, link_stop}
import visceljs.{Body, Make, SearchUtil}

import scala.collection.immutable.Map
import scala.scalajs.js
import scalatags.JsDom.all._
import scalatags.JsDom.implicits.stringFrag
import scalatags.JsDom.tags.SeqFrag
import scalatags.JsDom.tags2._


object Index {
	def gen(bookmarks: Map[String, Int], descriptions: Map[String, Description]): Body = {

		val bookmarkedNarrations: List[(Description, Int, Int)] =
			bookmarks.toList.flatMap { case (id, pos) =>
				descriptions.get(id).map { nr =>
					(nr, pos, nr.size - pos)
				}
			}

		val (hasNewPages, isCurrent) = bookmarkedNarrations.partition(_._3 > 15)
		val available = descriptions.values.toList.filter(d => !bookmarks.contains(d.id)).map(n => (n, 0, n.size))

		val inputQuery = Var("")
		val inputField = input(`type` := "textfield", tabindex := "1", onkeyup := ({ (inp: html.Input) =>
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

		val firstSelected = rescala.Signals.lift(filteredHasNewPages, filteredIsCurrent, filteredAvailable) {
			(n, c, a) => n.headOption.orElse(c.headOption).orElse(a.headOption).map(_._1)
		}



		val (totalBookmarks, unreadBookmarks) = bookmarkedNarrations.foldLeft((0, 0)) { case ((pos, unread), (nar, p, u)) => (pos + p, unread + (if (u > 0) u else 0)) }
		val totalBookmarkedPages = descriptions.filterKeys(bookmarks.contains).values.map(_.size).sum

		val searchForm = form(inputField, action := "", onsubmit := { () => firstSelected.now.foreach(gotoFront(_)); false })

		Body(id = "index", title = "Viscel",
			frag = List(
				Make.navigation(Make.fullscreenToggle("TFS"), searchForm, link_stop("stop")),
				Make.group(s"Updates ($unreadBookmarks)", filteredHasNewPages),
				Make.group(s"Bookmarks ($totalBookmarks/$totalBookmarkedPages)", filteredIsCurrent),
				Make.group(s"Available", filteredAvailable)))
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
