package viscel.narration.narrators

import org.scalactic.Accumulation._
import org.scalactic.Good
import viscel.narration.Queries._
import viscel.narration.Templates.{SimpleForward, archivePage}
import viscel.narration.interpretation.NarrationInterpretation._
import viscel.selection.ReportTools._
import viscel.selection.Selection
import viscel.shared.Vid
import viscel.store.Vurl.fromString
import viscel.store.{Chapter, ImageRef, Link, Normal, Volatile}

import scala.collection.immutable.Set

object Individual {


  val Inverloch = NarratorADT(
    Vid.from("NX_Inverloch"), "Inverloch",
    Range.inclusive(1, 5)
      .map(i => Link(s"http://inverloch.seraph-inn.com/volume$i.html",
                     Normal,
                     "archive" :: Nil)).toList,
    Condition(ContextW.map(_.link.data.exists("archive" == _)), {
      Selection.many("#main p:containsOwn(Chapter)").focus {
        Append(
          ElementW.map(chap => Chapter(chap.ownText()) :: Nil),
          Selection.many("a").wrapEach(extractMore))
      }
    }, {
      Decision(_.baseUri().endsWith("summaries.html"),
               Constant(Nil),
               queryImageNext("#main > p:nth-child(1) > img",
                              "#main a:containsOwn(Next)"))
    }))


  val Misfile = NarratorADT(Vid.from("NX_Misfile"), "Misfile",
    Link("http://www.misfile.com/archives.php?arc=1&displaymode=wide&", Volatile) :: Nil, {

      val wrapPage: Wrapper = {
        Decision(_.ownerDocument().location() == "http://www.misfile.com/archives.php?arc=34&displaymode=wide", Constant(Nil), {
          val elements_? = Selection
            .unique(".comiclist table.wide_gallery")
            .many("[id~=^comic_\\d+$] .picture a").focus {
            val element_? = Selection.unique("img").wrapOne {intoArticle}
            val origin_? = Selection.wrapOne(extractURL)
            Combination.of(element_?, origin_?) { (element, origin) =>
              element.copy(
                ref = element.ref.uriString.replace("/t", "/"),
                origin = origin,
                data = element.data - "width" - "height") :: Nil
            }
          }
          val next_? = queryNext("a.next")

          Append(elements_?, next_?)
        })
      }

      val wrapArchive: Wrapper = {
        val chapters_? = Selection.many("#comicbody a:matchesOwn(^Book #\\d+$)").wrapFlat { anchor =>
          extractMore(anchor).map { pointer =>
            Chapter(anchor.ownText()) :: pointer :: Nil
          }
        }
        // the list of chapters is also the first page, wrap this directly
        val firstPage_? = wrapPage

        Append(Constant(Chapter("Book #1") :: Nil), Append(firstPage_?, chapters_?))
      }

      PolicyDecision(wrapArchive, wrapPage)
    })



  val UnlikeMinerva = NarratorADT(Vid.from("NX_UnlikeMinerva"), "Unlike Minerva",
    Range.inclusive(1, 25).map(i => Link(s"http://www.unlikeminerva.com/archive/phase1.php?week=$i")).toList :::
      Range.inclusive(26, 130).map(i => Link(s"http://www.unlikeminerva.com/archive/index.php?week=$i")).toList,
    Selection.many("center > img[src~=http://www.unlikeminerva.com/archive/]").wrapEach { img =>
      withGood(intoArticle(img), extract(img.parent().nextElementSibling().text())) { (article, txt) =>
        article.copy(data = article.data.updated("longcomment", txt))
      }
    })


  val inlineCores = Set[NarratorADT](
    archivePage("NX_StandStillStaySilent", "Stand Still Stay Silent", "http://www.sssscomic.com/?id=archive",
      Focus(
        Selection.many("div[id~=adv\\d+Div]").wrap{advs => Good(advs.reverse) },
        queryMixedArchive("div.archivediv h2, div.archivediv a"),
        ),
      queryImage("img.comicnormal")),
    SimpleForward("NX_xkcd", "xkcd", "http://xkcd.com/1/", {
      // xkcd requires to sometimes ignore images, whics is why all is used here
      val assets_? = Selection.all("#comic img").wrapEach {
        intoArticle(_).map { article =>
          article.data.get("title").fold(article) { t =>
            article.copy(data = article.data.updated("longcomment", t))
          }
        }
      }
      val next_? = queryNext("a[rel=next]:not([href=#])")
      Append(assets_?, next_?)
    }),
    archivePage("NX_TheDreamer", "The Dreamer", "http://thedreamercomic.com/read-pages/",
      Focus(Selection.many("#archive_wrap > div").wrap{advs => Good(advs.reverse) },
            queryMixedArchive(".dreamer_flip_box_inner .flip_box_front .issue_title , .dreamer_flip_box_inner .issue_pages a")),
                queryImage("#comicnav div.imageWrap img")),
    SimpleForward("NX_CheerImgur", "Cheer by Forview", "http://imgur.com/a/GTprX/",
      {
        Selection.unique("div.post-images").many("div.post-image-container").wrapEach { div =>
          extract(ImageRef(
            ref = s"http://i.imgur.com/${div.attr("id")}.png",
            origin = "http://imgur.com/a/GTprX/"))
        }
      })
  )

  //    SimpleForward("NX_AvasDemon", "Ava’s Demon", "http://www.avasdemon.com/chapters.php",
//      Selection.many("table[id~=chapter\\d+_table]").wrap {
//        _.zipWithIndex.map { case (elem, idx) =>
//          Selection(elem).many("a").wrap { as =>
//            Good(Chapter(s"Chapter ${idx + 1}") ::
//              as.sortBy(_.text()).map { a =>
//                val origin = a.attr("abs:href")
//                val number = a.text()
//                val filename = number match {
//                  case "0222" => s"0243.gif"
//                  case "0367" => s"titanglow.gif"
//                  case "0368" => s"365.gif"
//                  case "0369" => s"366.gif"
//                  case "0370" => s"367.gif"
//                  case "0371" => s"368.gif"
//                  case "1799" => s"1790.png"
//                  case "0655" | "0762" | "1035" | "1130" | "1131" | "1132" | "1133" | "1134" |
//                       "1135" | "1136" | "1271" | "1272" | "1273" | "1274" | "1293" | "1294" |
//                       "1295" | "1384" | "1466" | "1788" => s"$number.png"
//                  case "0061" | "0353" | "0893" | "1546" | "1748" | "1749" | "1750" | "1918" => s"$number.gif"
//                  case _ => s"pages/$number.png"
//                }
//                val source = s"http://www.avasdemon.com/$filename"
//                ImageRef(source, origin)
//              })
//          }
//        }.combined.map(_.flatten)
//      }),

}
