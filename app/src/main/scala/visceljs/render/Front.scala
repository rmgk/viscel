package visceljs.render

import org.scalajs.dom.html
import rescala.default._
import scalatags.JsDom
import scalatags.JsDom.Frag
import scalatags.JsDom.all.{a, frag, href, id, stringAttr}
import scalatags.JsDom.implicits.stringFrag
import scalatags.JsDom.tags.{SeqFrag, body, div, h1}
import scalatags.JsDom.tags2.section
import viscel.shared.{ChapterPos, Contents, Gallery, SharedImage}
import visceljs.Definitions.class_preview
import visceljs.{Actions, Data, Definitions}

import scala.annotation.tailrec

class Front(actions: Actions) {

  def gen(dataS: Signal[Data]): Signal[JsDom.TypedTag[html.Body]] = {
    dataS.map { data =>
      val Data(vid, narration, Contents(gallery, chapters), bookmark) = data

      val top = h1(s"${narration.name} ($bookmark/${narration.size})")

      val navigation = Snippets.navigation(
        a(href := Definitions.path_main, "index"),
        a(href := Definitions.path_asset(data.move(_.first)), "first page"),
        Snippets.fullscreenToggle("fullscreen"),
        actions.postBookmark(0, data, "remove bookmark"),
        actions.postForceHint(vid, "force check"))

      val preview = {
        val preview1 = data.atPos(bookmark-3)
        val preview2 = preview1.next
        val preview3 = preview2.next
        div(class_preview)(
          List(preview1, preview2, preview3).map(p => p -> p.gallery.get)
            .collect { case (p, Some(anchor)) => a(href := Definitions.path_asset(p), Snippets.asset(anchor, data)) })
      }

      def chapterlist: Frag = {
        val assets = gallery.end

        def makeChapField(chap: String, size: Int, gallery: Gallery[SharedImage]): Frag = {
          val (remaining, links) = Range(size, 0, -1).foldLeft((gallery, List[Frag]())) { case ((gal, acc), i) =>
            val next = gal.prev(1)
            (next, a(href := Definitions.path_asset(data.move(_ => next)), s"$i") :: acc)
          }

          section(if (chap.isEmpty) links else frag(h1(chap), links))
        }


        @tailrec
        def build(apos: Int, assets: Gallery[SharedImage], chapters: List[ChapterPos], acc: List[Frag]): List[Frag] = chapters match {
          case ChapterPos(name, cpos) :: ctail =>
            build(cpos, assets.prev(apos - cpos), ctail, makeChapField(name, apos - cpos, assets) :: acc)
          case Nil => acc
        }

        SeqFrag(build(assets.pos, assets, chapters, Nil))

      }

      body(id := "front", top, navigation, preview, chapterlist)
    }
  }
}
