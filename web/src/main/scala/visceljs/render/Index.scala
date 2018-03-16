package visceljs.render

import org.scalajs.dom.html
import rescala._
import rescalatags._
import viscel.shared.Description
import visceljs.Definitions.link_tools
import visceljs.{Actions, Body, Make, SearchUtil}

import scala.collection.immutable.Map
import scala.scalajs.js
import scalatags.JsDom.all._
import scalatags.JsDom.implicits.stringFrag
import scalatags.JsDom.tags2._

class Index(actions: Actions, bookmarks: Signal[Map[String, Int]], descriptions: Signal[Map[String, Description]]) {

  var inputQueryString = ""

  def gen(): Body = {

    val fragS: Signal[Tag] = rescala.reactives.Signals.lift(bookmarks, descriptions) { (bookmarks, descriptions) =>

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
      val inputField = input(value := inputQueryString, `type` := "text", tabindex := "1", onkeyup := ({ (inp: html.Input) =>
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

      val firstSelected: Signal[Option[Description]] = Signal {
        filteredHasNewPages().headOption.orElse(filteredIsCurrent().headOption).orElse(filteredAvailable().headOption).map(_._1)
      }

      val callback: Signal[() => Boolean] = firstSelected map { sel => { () => sel.foreach(actions.gotoFront(_)); false } }

      val searchForm = form(cls := "pure-form")(inputField, onsubmit := callback)

      div(
        Make.navigation(Make.fullscreenToggle("fullscreen"), searchForm, link_tools("tools")),
        Make.group(s"Updates", actions, filteredHasNewPages),
        Make.group(s"Bookmarks", actions, filteredIsCurrent),
        Make.group(s"Available", actions, filteredAvailable))
    }

    val rendered = fragS.asFrag

    Body(id = "index", title = Var("Viscel"),
         frag = rendered)
  }


  def searchArea(narrations: List[Description], inputQuery: Signal[String]): HtmlTag = aside {

    val results = ol.render
    val filteredS: Signal[List[Description]] = inputQuery.map(query => SearchUtil.search(query, narrations.map(n => n.name -> n)))
    filteredS.observe { desc =>
      results.innerHTML = ""
      desc.foreach(nar => results.appendChild(li(actions.Tags.link_front(nar, nar.name)).render))
    }

    fieldset(legend("Search"), results)
  }

}
