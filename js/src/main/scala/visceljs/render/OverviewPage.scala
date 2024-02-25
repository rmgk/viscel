package visceljs.render

import org.scalajs.dom
import org.scalajs.dom.{Event, html}
import rescala.default.*
import rescala.extra.Tags.*
import scalatags.JsDom
import scalatags.JsDom.TypedTag
import scalatags.JsDom.all.*
import scalatags.JsDom.implicits.stringFrag
import viscel.shared.{Bookmark, Description, Vid}
import visceljs.Definitions.link_tools
import visceljs.{Definitions, MetaInfo, SearchUtil}

import scala.collection.immutable.Map

case class FrontPageEntry(id: Vid, description: Option[Description], bookmark: Option[Bookmark]) {
  val name: String             = description.fold(id.str)(_.name)
  val bookmarkPosition: Int    = bookmark.fold(0)(_.position)
  val size: Int                = description.fold(0)(_.size)
  val newPages: Int            = size - bookmarkPosition
  val sortOrder: (Int, String) = (-newPages, name)
  def linked: Boolean          = description.fold(false)(_.linked)
  def hasNewPages: Boolean     = newPages > 15
  def noBookmark: Boolean      = bookmarkPosition == 0
  def bookmarksFirst: Boolean  = bookmarkPosition == 1 && newPages > 0
  def recentOrder: Long        = -bookmark.fold(0L)(_.timestamp)
}

class OverviewPage(
    meta: MetaInfo,
    bookmarks: Signal[Map[Vid, Bookmark]],
    descriptions: Signal[Map[Vid, Description]]
) {

  def gen(): TypedTag[html.Body] = {

    val entriesS = Signal.lift(bookmarks, descriptions) { (bookmarks, descriptions) =>
      (bookmarks.keys ++ descriptions.keys).toList.distinct.map { id =>
        FrontPageEntry(id, descriptions.get(id), bookmarks.get(id))
      }
    }.withDefault(Nil)

    val searchInput = Evt[Event]()
    val searchString: Signal[String] = searchInput.map { ke =>
      val sv = ke.currentTarget.asInstanceOf[html.Input].value.toString.toLowerCase
      sv
    }.hold("")
    val inputField = input(value := searchString, `type` := "text", tabindex := "1", oninput := searchInput)

    val groupsS = entriesS.map { unsorted =>
      val entries                  = unsorted.sortBy(_.sortOrder)
      val (available, remaining1)  = entries.partition(_.noBookmark)
      val (marked, remaining2)     = remaining1.partition(_.bookmarksFirst)
      val (remaining3, noNewPages) = remaining2.partition(_.hasNewPages)
      val recent                   = remaining3.sortBy(_.recentOrder).take(12)
      val updates                  = remaining3.diff(recent)

      Seq(
        "Recent"    -> recent,
        "Updates"   -> updates,
        "Marked"    -> marked,
        "Bookmarks" -> noNewPages,
        "Available" -> available
      )
    }

    val sortedFilteredGroups = Signal {
      groupsS.value.map {
        case (n, g) =>
          n -> SearchUtil.search(searchString.value, g.map(e => e.name -> e))
      }
    }

    val callback: Signal[() => Boolean] = sortedFilteredGroups.map { gs =>
      val displayOrder       = gs.map(_._2)
      val first: Option[Vid] = displayOrder.find(_.nonEmpty).map { _.head.id }
      () => { first.foreach(f => dom.window.location.hash = Definitions.path_front(f)); false }
    }

    val searchForm = form(inputField, onsubmit := callback)

    val groupTags: Signal[Seq[JsDom.TypedTag[dom.Element]]] = sortedFilteredGroups.map { g =>
      g.map {
        case (name, content) =>
          Snippets.group(name, content)
      }
    }

    body(
      id := "index",
      Snippets.meta(meta).asModifier,
      Snippets.navigation(Snippets.fullscreenToggle("fullscreen"), searchForm, link_tools("tools")),
      SignalTagListToScalatags(groupTags).asModifierL
    )
  }

}
