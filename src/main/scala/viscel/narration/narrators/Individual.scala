package viscel.narration.narrators

import viscel.narration.NarratorADT
import viscel.narration.Queries._
import viscel.narration.Templates.{SimpleForward, archivePage}
import viscel.selektiv.Narration._
import viscel.selektiv.ReportTools._
import viscel.selektiv.Selection
import viscel.shared.Vid
import viscel.store.v4.DataRow

import scala.collection.immutable.Set

object Individual {


  val Inverloch = NarratorADT(
    Vid.from("NX_Inverloch"), "Inverloch",
    Range.inclusive(1, 5)
      .map(i => DataRow.Link(s"http://inverloch.seraph-inn.com/volume$i.html",
                     "archive" :: Nil)).toList,
    Condition(ContextW.map(_.context.exists("archive" == _)), {
      Selection.many("#main p:containsOwn(Chapter)").focus {
        Append(
          ElementW.map(chap => DataRow.Chapter(chap.ownText()) :: Nil),
          Selection.many("a").wrapEach(extractMore))
      }
    }, {
      Condition(ContextW.map {_.location.endsWith("summaries.html")},
                Constant(Nil),
                queryImageNext("#main > p:nth-child(1) > img",
                               "#main a:containsOwn(Next)"))
    }))

  val UnlikeMinerva = NarratorADT(
    Vid.from("NX_UnlikeMinerva"),
    "Unlike Minerva",
    Range.inclusive(1, 25).map(i => DataRow.Link(s"http://www.unlikeminerva.com/archive/phase1.php?week=$i")).toList :::
    Range.inclusive(26, 130).map(i => DataRow.Link(s"http://www.unlikeminerva.com/archive/index.php?week=$i")).toList,

    Selection.many("center > img[src~=/archive/]")
    .wrapEach { img =>
      val article = extractArticle(img)
      val txt = extract(img.parent().nextElementSibling().text())
      article.copy(data = article.data ::: List("title", txt))
    })


  val inlineCores = Set[NarratorADT](
    archivePage("NX_StandStillStaySilent", "Stand Still Stay Silent", "http://www.sssscomic.com/?id=archive",
      Focus(
        Selection.many("div[id~=adv\\d+Div]").wrap{advs => advs.reverse },
        queryMixedArchive("div.archivediv h2, div.archivediv a")
        ),
      queryImage("img.comicnormal")),
    SimpleForward("NX_xkcd", "xkcd", "http://xkcd.com/1/", {
      // xkcd requires to sometimes ignore images, which is why all is used here
      val assets_? = Selection.all("#comic img").wrapEach {extractArticle}
      val next_? = queryNext("a[rel=next]:not([href=#])")
      Append(assets_?, next_?)
    }),
    archivePage("NX_TheDreamer", "The Dreamer", "http://thedreamercomic.com/read-pages/",
      Focus(Selection.many("#archive_wrap > div").wrap{advs => advs.reverse },
            queryMixedArchive(".dreamer_flip_box_inner .flip_box_front .issue_title , .dreamer_flip_box_inner .issue_pages a")),
                queryImage("#comicnav div.imageWrap img"))
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
