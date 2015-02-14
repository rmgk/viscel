package visceljs.render

import viscel.shared.Description
import visceljs.Definitions.{link_front, link_stop}
import visceljs.{Body, Make}

import scala.Predef.$conforms
import scala.collection.immutable.Map
import scalatags.JsDom.implicits.stringFrag
import scalatags.JsDom.tags.SeqFrag


object Index {
	def gen(bookmarks: Map[String, Int], descriptions: Map[String, Description]): Body = {

		val bookmarkedNarrations: List[(Description, Int, Int)] =
			bookmarks.toList.map { case (id, pos) =>
				descriptions.get(id).map { nr =>
					(nr, pos, nr.size - pos)
				}
			}.flatten

		val (hasNewPages, isCurrent) = bookmarkedNarrations.partition(_._3 > 0)

		val unreadTags = hasNewPages.sortBy(-_._3).map {
			case (nr, pos, unread) => link_front(nr, s"${ nr.name } ($unread)")
		}
		val currentTags = isCurrent.sortBy(_._1.name).map {
			case (nr, pos, unread) => link_front(nr, s"${ nr.name }${if (unread >= 0) "" else s" ($unread)"}")
		}

		val (totalBookmarks, unreadBookmarks) = bookmarkedNarrations.foldLeft((0, 0)){case ((pos, unread), (nar, p, u)) => (pos + p, unread + (if (u > 0) u else 0))}
		val totalBookmarkedPages = descriptions.filterKeys(bookmarks.contains).values.map(_.size).sum

		Body(id = "index", title = "Viscel",
			frag = List(
				Make.group(s"New Pages ($unreadBookmarks)", unreadTags),
				Make.group(s"Bookmarks ($totalBookmarks/$totalBookmarkedPages)", currentTags),
				Make.navigation(Make.fullscreenToggle("TFS"),link_stop("stop")),
				Make.searchArea(descriptions.values.toList)))
	}
}
