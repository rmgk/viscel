package visceljs.render

import org.scalajs.dom.html
import scalatags.JsDom
import scalatags.JsDom.Frag
import scalatags.JsDom.all.{a, frag, href, id, stringAttr}
import scalatags.JsDom.implicits.stringFrag
import scalatags.JsDom.tags.{SeqFrag, body, div, h1,p }
import scalatags.JsDom.tags2.section
import viscel.shared.{Blob, Bookmark, ChapterPos, Contents, Description, SharedImage, Vid}
import visceljs.Definitions.class_preview
import visceljs.{Actions, Definitions}

class Front(actions: Actions) {

  def gen(vid: Vid, description: Description, contents: Contents, bookmark: Bookmark): JsDom.TypedTag[html.Body] = {

    val top =
      h1(s"${description.name} (${bookmark.position}/${contents.gallery.size})")

    val navigation = Snippets.navigation(
      a(href := Definitions.path_main, "index"),
      a(href := Definitions.path_asset(vid, 0), "first page"),
      Snippets.fullscreenToggle("fullscreen"),
      actions.postBookmark(vid, 0, bookmark.position, None, "remove bookmark"),
      actions.postForceHint(vid, "force check"))

    val preview = {
      val bmed = contents.gallery.lift(bookmark.position - 1)
      val warnings = if (!bookmark.sha1.contains(bmed.map(_.blob.sha1).getOrElse(""))) {
        frag(h1("Warning: bookmark mismatch. Left: bookmarked position. Right: bookmarked image"),
             div(class_preview)(
               bmed.map(asst => a(href := Definitions.path_asset(vid, bookmark.position - 1), Snippets.asset(asst))).toSeq)(
               a(Snippets.asset(SharedImage(bookmark.origin.getOrElse(""), Blob(bookmark.sha1.get, "image"), Map.empty)))
             ),
             p(s"gallery max: ${contents.gallery.size}"),

             p(s"position: $bmed"),
             p(s"image: $bookmark)")
             )
      } else frag()
        val start = math.max(0, bookmark.position - 3)
        frag(warnings, div(class_preview)(
          Range(start, start + 3)
            .map(p => p -> contents.gallery.lift(p))
            .collect { case (p, Some(anchor)) => a(href := Definitions.path_asset(vid, p), Snippets.asset(anchor)) }))
    }


    body(id := "front", top, navigation, preview, chapterlist(vid, contents.chapters, contents.gallery.size))
  }

  def chapterlist(vid: Vid, chapters: List[ChapterPos], last: Int): Frag = {
    val pairs = (ChapterPos("", last) :: chapters).reverse.sliding(2)

    def chaps(start: Int, end: Int) = {
      Range(start, end).map { si =>
        a(href := Definitions.path_asset(vid, si), s"${si - start + 1}")
      }
    }

    frag(pairs.map {
      case List(single)     => section()
      case List(start, end) =>
        val links = chaps(start.pos, end.pos)
        section(if (start.name.isEmpty) links else frag(h1(start.name), links))
    }.toSeq)
  }
}
