package visceljs.render

import rescala._
import rescalatags._
import viscel.shared.{ChapterPos, Contents, Gallery, SharedImage}
import visceljs.Definitions.{class_chapters, class_preview, link_asset, link_index}
import visceljs.{Actions, Body, Data, Make}

import scala.annotation.tailrec
import scalatags.JsDom.Frag
import scalatags.JsDom.all.Tag
import scalatags.JsDom.implicits.stringFrag
import scalatags.JsDom.tags.{SeqFrag, div, fieldset, legend, span, h1}
import scalatags.JsDom.tags2.{article, section}

object Front {


	def gen(dataS: Signal[Data]): Body = {

		val fragS: Signal[Tag] = dataS.map { data =>
			val Data(narration, Contents(gallery, chapters), bookmark, _) = data

			val top = h1(s"${narration.name} ($bookmark/${narration.size})")

			val navigation = Make.navigation(
				link_index("index"),
				Make.fullscreenToggle("TFS"),
				link_asset(data.move(_.first))("first"),
				Make.postBookmark(0, data, _ => Actions.gotoFront(narration, scrolltop = false), "remove"),
				Make.postForceHint(narration, "force"))

			val preview = {
				val preview1 = data.move(_.next(bookmark - 1).prev(2))
				val preview2 = preview1.next
				val preview3 = preview2.next
				section(class_preview)(
					List(preview1, preview2, preview3).map(p => p -> p.gallery.get)
						.collect { case (p, Some(a)) => link_asset(p)(Make.asset(a, data): _*) })
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
			div(top, navigation, preview, chapterlist)
		}.withDefault(span("loading please wait"))


		Body(id = "front", title = dataS.map(_.description.name),
			frag = fragS.asFrag)

	}
}
