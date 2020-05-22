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
}
