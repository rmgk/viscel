package visceljs.render

import org.scalajs.dom.html
import scalatags.JsDom
import scalatags.JsDom.Frag
import scalatags.JsDom.all.{a, frag, href, id, stringAttr}
import scalatags.JsDom.implicits.stringFrag
import scalatags.JsDom.tags.{SeqFrag, body, div, h1}
import scalatags.JsDom.tags2.section
import viscel.shared.{ChapterPos, Contents, Description, Log, Vid}
import visceljs.Definitions.class_preview
import visceljs.{Actions, Definitions}

class Front(actions: Actions) {

  def gen(vid: Vid, description: Description, contents: Contents, bookmark: Int): JsDom.TypedTag[html.Body] = {



      val top =
        h1(s"${description.name} ($bookmark/${contents.gallery.size})")

      val navigation = Snippets.navigation(
        a(href := Definitions.path_main, "index"),
        a(href := Definitions.path_asset(vid, 0), "first page"),
        Snippets.fullscreenToggle("fullscreen"),
        actions.postBookmark(vid, 0, bookmark, None, "remove bookmark"),
        actions.postForceHint(vid, "force check"))

      val preview = {
        val start = math.max(0, bookmark - 3)
        div(class_preview)(
          Range(start, start+3).map(p => p -> contents.gallery.lift(p))
            .collect { case (p, Some(anchor)) => a(href := Definitions.path_asset(vid, p), Snippets.asset(anchor)) })
      }



      body(id := "front", top, navigation, preview, chapterlist(vid, contents.chapters, contents.gallery.size))
  }

    def chapterlist(vid: Vid, chapters: List[ChapterPos], last: Int): Frag = {
      Log.JS.info(chapters.toString())
      val pairs = (ChapterPos("", last) :: chapters).reverse.sliding(2)

      def chaps(start: Int, end: Int) = {
        Range(start, end).map { si =>
          a(href := Definitions.path_asset(vid, si), s"${si - start + 1}")
        }
      }

      frag(pairs.map {
        case List(single) => section()
        case List(start, end) =>
          val links = chaps(start.pos, end.pos)
          section(if (start.name.isEmpty) links else frag(h1(start.name), links))
      }.toSeq)
    }
}
