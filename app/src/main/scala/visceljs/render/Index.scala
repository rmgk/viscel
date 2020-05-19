package visceljs.render

import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.Event
import rescala.default._
import scalatags.JsDom.TypedTag
import scalatags.JsDom.all._
import scalatags.JsDom.implicits.stringFrag
import viscel.shared.{Bookmark, Description, Vid}
import visceljs.Definitions.link_tools
import rescala.extra.Tags._
import scalatags.JsDom
import visceljs.{Actions, Make, MetaInfo, SearchUtil}

import scala.collection.immutable.Map

trait FrontPageEntry {
  def sortOrder: Int
  def newPages: Int
  def hasNewPages: Boolean = newPages > 15
  def description: Description
  def name: String = description.name
  def bookmarkPosition: Int
  def noBookmark: Boolean = bookmarkPosition == 0
  def bookmarksFirst: Boolean = bookmarkPosition == 1 && newPages > 0
  def recentOrder: Long
  def id: Vid
}

case class BookmarkedEntry(id: Vid, description: Description, bookmark: Bookmark) extends FrontPageEntry {
  def sortOrder: Int = bookmark.position - description.size
  def newPages: Int = description.size - bookmark.position
  override def bookmarkPosition: Int = bookmark.position
  override def recentOrder: Long = -bookmark.timestamp
}

case class AvailableEntry(id: Vid, description: Description) extends FrontPageEntry {
  override def sortOrder: Int = -description.size
  def newPages = 0
  override def bookmarkPosition: Int = 0
  override def recentOrder: Long = 0
}

class Index(meta: MetaInfo, actions: Actions, bookmarks: Signal[Map[Vid, Bookmark]], descriptions: Signal[Map[Vid, Description]]) {

  def gen(): TypedTag[html.Body] = {

    val entries = Signals.lift(bookmarks, descriptions) { (bookmarks, descriptions) =>

      val bookmarked: List[FrontPageEntry] =
        bookmarks.toList.flatMap { case (id, bookmark) =>
          descriptions.get(id).map { desc =>
            BookmarkedEntry(id, desc, bookmark)
          }
        }
      val available: List[FrontPageEntry] =
        descriptions.iterator.collect{
          case (id, desc) if !bookmarks.contains(id) => AvailableEntry(id, desc)
        }.toList

      bookmarked.reverse_:::(available)
    }.withDefault(Nil)

    val searchInput = Evt[Event]
    val searchString: Signal[String] = searchInput.map { ke =>
      val sv = ke.currentTarget.asInstanceOf[html.Input].value.toString.toLowerCase
      println(s"search val $sv")
      sv
    }.latest("")
    val inputField = input(value := searchString, `type` := "text", tabindex := "1",
                           oninput := searchInput)

    def searchable(l : List[FrontPageEntry]) = l.map(e => e.name -> e)

    val sortedFilteredEntries = Signal {
      val query = searchString.value
      if (query.isEmpty) entries.value.sortBy(_.sortOrder)
      else SearchUtil.search(query, searchable(entries.value))
    }

    val groups = sortedFilteredEntries.map { entries =>

      val (available, bookmarked) = entries.partition(_.noBookmark)
      val (marked, bookmarked2) = bookmarked.partition(_.bookmarksFirst)
      val (bookMarked3, noNewPages) = bookmarked2.partition(_.hasNewPages)
      val recent = bookMarked3.sortBy(_.recentOrder).take(12)
      val normal = bookMarked3.filterNot(recent.contains)

      Seq("Recent" -> recent,
          "Updates" -> normal,
          "Marked" -> marked,
          "Bookmarks" -> noNewPages,
          "Available" -> available)
    }


    val callback: Signal[() => Boolean] = groups.map { gs =>
      val displayOrder = gs.map(_._2)
      val first: Option[Vid] = displayOrder.find(_.nonEmpty).map{ _.head.id}
      () => {first.foreach(actions.gotoFront); false}
    }

    val searchForm = form(inputField, onsubmit := callback)

    //println(s"groups: ${groups.now}")


    val groupTags: Signal[Seq[JsDom.TypedTag[dom.Element]]] = groups.map{ g =>
      val groupNames = Seq("Recent",
                           "Updates",
                           "Marked",
                           "Bookmarks",
                           "Available")
      groupNames.map { gn =>
        Make.group(gn, actions, g.toMap.apply(gn))
      }
    }


    body(id := "index",
         Make.navigation(Make.fullscreenToggle("fullscreen"), searchForm, link_tools("tools")),
         Make.meta(meta),
         groupTags.asModifierL
         )
  }

}
