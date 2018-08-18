package visceljs.render

import org.scalajs.dom.html
import rescala.default._
import viscel.shared.{ChapterPos, Contents, Gallery, SharedImage}
import visceljs.Definitions.{class_chapters, class_preview}
import visceljs.{Actions, Data, Make}

import scala.annotation.tailrec
import scalatags.JsDom
import scalatags.JsDom.Frag
import scalatags.JsDom.all.{Tag, id, stringAttr}
import scalatags.JsDom.implicits.stringFrag
import scalatags.JsDom.tags.{SeqFrag, body, fieldset, h1, legend}
import scalatags.JsDom.tags2.{article, section}

class Front(actions: Actions) {

  import actions._

  def gen(dataS: Signal[Data]): Signal[JsDom.TypedTag[html.Body]] = {
    dataS.map { data =>
      val Data(narration, Contents(gallery, chapters), bookmark) = data

      val top = h1(s"${narration.name} ($bookmark/${narration.size})")

      val navigation = Make.navigation(
        button_index("index"),
        button_asset(data.move(_.first))("first page"),
        Make.fullscreenToggle("fullscreen"),
        postBookmark(0, data, _ => gotoFront(data.description), "remove bookmark"),
        postForceHint(narration, "force check"))

      val preview = {
        val preview1 = data.atPos(bookmark-3)
        val preview2 = preview1.next
        val preview3 = preview2.next
        section(class_preview)(
          List(preview1, preview2, preview3).map(p => p -> p.gallery.get)
            .collect { case (p, Some(a)) => link_asset(p)(Make.asset(a, data)) })
      }

      def chapterlist: Tag = {
        val assets = gallery.end

        def makeChapField(chap: String, size: Int, gallery: Gallery[SharedImage]): Frag = {
          val (remaining, links) = Range(size, 0, -1).foldLeft((gallery, List[Frag]())) { case ((gal, acc), i) =>
            val next = gal.prev(1)
            (next, link_asset(data.move(_ => next))(s"$i") :: stringFrag(" ") :: acc)
          }

          article(fieldset(legend(chap), links))
        }


        @tailrec
        def build(apos: Int, assets: Gallery[SharedImage], chapters: List[ChapterPos], acc: List[Frag]): List[Frag] = chapters match {
          case ChapterPos(name, cpos) :: ctail =>
            build(cpos, assets.prev(apos - cpos), ctail, makeChapField(name, apos - cpos, assets) :: acc)
          case Nil => acc
        }

        section(class_chapters)(build(assets.pos, assets, chapters, Nil))

      }

      body(id := "front", top, navigation, preview, chapterlist)
    }
  }
}
