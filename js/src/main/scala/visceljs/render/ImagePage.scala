package visceljs.render

import org.scalajs.dom
import org.scalajs.dom.MouseEvent
import org.scalajs.dom.html.Body
import rescala.default.*
import scalatags.JsDom
import scalatags.JsDom.all.{
  HtmlTag, Modifier, Tag, a, bindJsAnyLike, body, href, id, onclick, p, rel, span, stringAttr, stringFrag, title
}
import scalatags.JsDom.tags2.{article, main}
import scalatags.JsDom.attrs.{disabled, style}
import viscel.shared.{Bookmark, Contents, Vid}
import visceljs.Definitions.lcButton
import visceljs.Navigation.*
import visceljs.{Actions, Definitions, Icons}
import rescala.extra.Tags.*

class ImagePage(act: Actions) {

  def onLeftClickPrevNext(handler: Navigate => Unit): Modifier =
    onclick := { (e: MouseEvent) =>
      val node = e.currentTarget.asInstanceOf[dom.html.Element]
      if (e.button == 0 && dom.document.getSelection().isCollapsed && dom.window.getSelection().isCollapsed) {
        e.preventDefault()
        val relx   = e.clientX - node.offsetLeft
        val border = math.max(node.offsetWidth / 10, 100)
        if (relx < border) handler(Prev)
        else handler(Next)
      }
    }

  def gen(
      vid: Vid,
      position: Position,
      bookmark: Bookmark,
      contents: Contents,
      fitType: Signal[FitType],
      navigate: Evt[Navigate]
  ): JsDom.TypedTag[Body] = {

    val mainPart: Tag = {
      contents.gallery.lift(position.cur).fold[Tag](p(s"invalid position")) { asst =>
        article(Snippets.asset(asst, style := fitType.map(Snippets.imageStyle))(
          asst.data.get("title").fold[Option[Tag]](None)(t => Some(p(t))).toSeq: _*
        ))
      }
    }

    val navigation: HtmlTag = {
      val prev = position.mov(-1)
      val next = position.mov(1)
      Snippets.navigation(
        a(Icons.prev, rel := "prev", title := "previous page")(if (prev.cur == position.cur) disabled
        else href         := Definitions.path_asset(vid, prev.cur)),
        a(href := Definitions.path_front(vid), Icons.front, title := "back to front page"),
        Snippets.fullscreenToggle(Icons.maximize, Icons.minimize, title := "toggle fullscreen"),
        lcButton(
          navigate.fire(Mode(fitType.now.next)),
          Icons.modus,
          fitType.map(ft => span(s" $ft")).asModifier,
          title := "cycle image display mode"
        ),
        act.postBookmark(
          vid,
          position.cur + 1,
          bookmark.position,
          contents.gallery.lift(position.cur),
          Icons.bookmark,
          title := "save bookmark"
        ),
        a(href := contents.gallery.lift(position.cur).fold("")(_.origin), rel := "noreferrer")(
          Icons.externalLink,
          title := "visit original page"
        ),
        a(Icons.next, rel := "next", title := "next")(if (next.cur == position.cur) disabled
        else href         := Definitions.path_asset(vid, next.cur))
      )
    }

    val mainSection = main(mainPart, onLeftClickPrevNext(navigate.fire))
    body(id := "view", mainSection, navigation)

  }

}
