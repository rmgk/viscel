package viscel.narration.narrators

import org.scalactic.Accumulation._
import org.scalactic.Good
import org.scalactic.TypeCheckedTripleEquals._
import viscel.narration.Queries._
import viscel.narration.Templates
import viscel.narration.Templates.{SimpleForward, archivePage}
import viscel.narration.interpretation.NarrationInterpretation.{Alternative, Append, Combination, Constant, Decision, ElementWrapper, LinkDataDecision, PolicyDecision, Shuffle, TransformUrls, Wrapper, NarratorADT => TNarratorADT, strNarratorADT => NarratorADT}
import viscel.selection.ReportTools._
import viscel.selection.Selection
import viscel.store.Vurl.fromString
import viscel.store.{Chapter, ImageRef, Link, Normal, Volatile, Vurl, WebContent}

import scala.collection.immutable.Set

object Individual {

  val Candi = NarratorADT("NX_Candi", "Candi", Link("http://candicomics.com/archive.html", Volatile, "archive" :: Nil) :: Nil, {

    val wrapVolume: Wrapper =
      Selection.many("#candimidd > table > tbody > tr > td:nth-child(2n) a").wrapFlat {elementIntoChapterPointer}

    val wrapArchive: Wrapper = {
      val volumes_? : Wrapper = Shuffle.of(Selection.many("#candimidd > p:nth-child(2) a").wrapEach { e =>
        extractMore(e).map(morePolicy(Volatile))
      })(_.drop(1))
      // the list of volumes is also the first volume, so we append that
      Append(wrapVolume, volumes_?)
    }

    PolicyDecision(
      LinkDataDecision(_.exists("archive" == _), wrapArchive, wrapVolume),
      queryImageNext("#comicplace > span > img", "#comicnav a:has(img#next_day2)"))
  })



  val Flipside = Templates.archivePage("NX_Flipside", "Flipside", "http://flipside.keenspot.com/chapters.php",
    {
      Selection.many("td:matches(Chapter|Intermission)").focus {
        val name = Decision[List[WebContent]](_.text.contains("Chapter"),
          Selection.unique("td:root > div:first-child").wrapEach { e => Good(Chapter(e.text())) },
          Selection.unique("p > b").wrapEach { e => Good(Chapter(e.text)) }
        )

        val pages = Shuffle.of(Selection.many("a").wrapEach(extractMore)) {_.distinct}

        Append[WebContent](name, pages)
      }
    },
    Selection.unique("img.ksc").wrapEach(intoArticle))


  val Inverloch = NarratorADT("NX_Inverloch", "Inverloch",
    Range.inclusive(1, 5).map(i => Link(s"http://inverloch.seraph-inn.com/volume$i.html", Normal, "archive" :: Nil)).toList,
    LinkDataDecision(_.exists("archive" == _), {
      Selection.many("#main p:containsOwn(Chapter)").focus {
        Append(
          ElementWrapper(chap => extract(Chapter(chap.ownText()) :: Nil)),
          Selection.many("a").wrapEach(extractMore))
      }
    }, {
      Decision(_.baseUri().endsWith("summaries.html"),
        Constant(Good(Nil)),
        queryImageNext("#main > p:nth-child(1) > img", "#main a:containsOwn(Next)"))
    }))

  object JayNaylor {
    def common(id: String, name: String, archiveUri: Vurl) = Templates.archivePage(id, name, archiveUri,
      Selection.many("#chapters li > a").wrapFlat {elementIntoChapterPointer},
      queryImages("#comicentry .content img"))

    def BetterDays = common("NX_BetterDays", "Better Days", "http://jaynaylor.com/betterdays/archives/chapter-1-honest-girls/")

    def OriginalLife = common("NX_OriginalLife", "Original Life", "http://jaynaylor.com/originallife/")

  }


  val Misfile = NarratorADT("NX_Misfile", "Misfile",
    Link("http://www.misfile.com/archives.php?arc=1&displaymode=wide&", Volatile) :: Nil, {

      val wrapPage: Wrapper = {
        Decision(_.ownerDocument().location() == "http://www.misfile.com/archives.php?arc=34&displaymode=wide", Constant(Good(Nil)), {
          val elements_? = Selection
            .unique(".comiclist table.wide_gallery")
            .many("[id~=^comic_\\d+$] .picture a").focus {
            val element_? = Selection.unique("img").wrapOne {intoArticle}
            val origin_? = ElementWrapper(extractURL)
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

        Append(Constant(Good(Chapter("Book #1") :: Nil)), Append(firstPage_?, chapters_?))
      }

      PolicyDecision(wrapArchive, wrapPage)
    })


  val NamirDeiter = NarratorADT("NX_NamirDeiter", "Namir Deiter",
    Link(s"http://www.namirdeiter.com/archive/index.php?year=1", Volatile, "archive" :: Nil) :: Nil, {
      def wrapIssue: Wrapper = Selection.many("table #arctitle > a").wrapFlat(elementIntoChapterPointer)

      PolicyDecision(
        LinkDataDecision(_.exists("archive" == _), {
          Append(
            wrapIssue,
            Selection.many("body > center > div > center > h2 > a").wrapEach(extractMore(_).map(morePolicy(Volatile))))
        },
          wrapIssue),
        Decision(_.ownerDocument().baseUri() == "http://www.namirdeiter.com/comics/index.php?date=20020819", Constant(Good(Nil)),
          Decision(_.ownerDocument().baseUri() == "http://www.namirdeiter.com/", Constant(Good(Nil)),
            queryImageInAnchor("body > center > div > center:nth-child(3) > table center img"))))
    })


  val YouSayItFirst = NarratorADT("NX_YouSayItFirst", "You Say It First",
    Range.inclusive(1, 9).map(i => Link(s"http://www.yousayitfirst.com/archive/index.php?year=$i", data = List("archive"))).toList,

    LinkDataDecision(_.exists("archive" == _),
      Selection.many("table #number a").wrapFlat(elementIntoChapterPointer),
      Decision({_.ownerDocument().baseUri() == "http://www.yousayitfirst.com/"}, Constant(Good(Nil)),
        queryImageInAnchor("body > center > div.mainwindow > center:nth-child(2) > table center img"))))


  val UnlikeMinerva = NarratorADT("NX_UnlikeMinerva", "Unlike Minerva",
    Range.inclusive(1, 25).map(i => Link(s"http://www.unlikeminerva.com/archive/phase1.php?week=$i")).toList :::
      Range.inclusive(26, 130).map(i => Link(s"http://www.unlikeminerva.com/archive/index.php?week=$i")).toList,
    Selection.many("center > img[src~=http://www.unlikeminerva.com/archive/]").wrapEach { img =>
      withGood(intoArticle(img), extract(img.parent().nextElementSibling().text())) { (article, txt) =>
        article.copy(data = article.data.updated("longcomment", txt))
      }
    })


  val inlineCores = Set[TNarratorADT](
    archivePage("NX_Twokinds", "Twokinds", "http://twokinds.keenspot.com/archive/",
      queryMixedArchive("#content .chapter h2 , #content .chapter-links a"),
      queryImage("#content article.comic img[alt=Comic Page]")
    ),
    archivePage("NX_Fragile", "Fragile", "http://www.fragilestory.com/archive",
      Selection.unique("#content_post").many(".c_arch:has(div.a_2)").focus {
        val chapter_? = Selection.first("div.a_2 > p").wrapEach(extractChapter)
        val pages_? = Selection.many("a").wrapEach(extractMore)
        Append(chapter_?, pages_?)
      },
      queryImage("#comic_strip > a > img")),
    SimpleForward("NX_CliqueRefresh", "Clique Refresh", "http://cliquerefresh.com/comic/start-it-up/", queryImageInAnchor("#cc-comic")),
    archivePage("NX_PennyAndAggie", "Penny & Aggie", "http://www.pennyandaggie.com/index.php?p=1",
      Selection.many("form[name=jump] > select[name=menu] > option[value]").wrapFlat(elementIntoChapterPointer),
      queryImageNext(".comicImage", "center > span.a11pixbluelinks > div.mainNav > a:has(img[src~=next_day.gif])")),
    archivePage("NX_MegaTokyo", "MegaTokyo", "http://megatokyo.com/archive.php",
      Selection.many("div.content:has(a[id~=^C-\\d+$])").focus {
        val chapter_? = ElementWrapper(chap => extract(Chapter(chap.child(0).text()) :: Nil))
        val elements_? = Selection.many("li a").wrapEach(extractMore)
        Append(chapter_?, elements_?)
      }, {
        Decision(_.ownerDocument().location().endsWith("megatokyo.com/strip/1428"),
          Constant(Good(List(Link(Vurl.fromString("http://megatokyo.com/strip/1429"))))),
          queryImageNext("#strip img", "#comic .next a"))
      }),
    SimpleForward("NX_WhatBirdsKnow", "What Birds Know", "http://fribergthorelli.com/wbk/index.php/page-1/", queryImageNext("#comic-1 img", "a.navi-next")),
    archivePage("NX_TodayNothingHappened", "Today Nothing Happened", "http://www.todaynothinghappened.com/archive.php",
      Selection.many("#wrapper > div.rant a.link").wrapEach(extractMore),
      queryImage("#comic > img")),
    SimpleForward("NX_Dreamless", "Dreamless", "http://dreamless.keenspot.com/d/20090105.html",
      Alternative(queryImageNext("img.ksc", "a:has(#next_day1)"),queryNext("a:has(#next_day1)"))),
    archivePage("NX_PhoenixRequiem", "The Phoenix Requiem", "http://requiem.seraph-inn.com/archives.html",
      Selection.many("#container div.main > table tr:contains(Chapter)").focus {
        val chapter_? = ElementWrapper(chap => extract(Chapter(chap.child(0).text()) :: Nil))
        val elements_? = Selection.many("a").wrapEach(extractMore)
        Append(chapter_?, elements_?)
      },
      queryImage("#container img[src~=^pages/]")),
    SimpleForward("NX_ErrantStory", "Errant Story", "http://www.errantstory.com/2002-11-04/15", queryImageNext("#comic > img", "#column > div.nav > h4.nav-next > a")),
    archivePage("NX_StandStillStaySilent", "Stand Still Stay Silent", "http://www.sssscomic.com/?id=archive",
      queryMixedArchive("#main_text div.archivediv h2, #main_text div.archivediv a"),
      queryImage("#wrapper2 img")),
    archivePage("NX_DreamScar", "dream*scar", "http://dream-scar.net/archive.php",
      Selection.many("#static > b , #static > a").wrapEach { elem =>
        if (elem.tagName() === "b") extract {Chapter(elem.text())}
        else extractMore(elem)
      },
      queryImage("#comic")),
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
    SimpleForward("NX_xkcd", "xkcd", "http://xkcd.com/1/",
      {
        val assets_? = Selection.all("#comic img").wrapEach{ intoArticle(_).map { article =>
          article.data.get("title").fold(article)(t => article.copy(data = article.data.updated("longcomment", t)))}}
        val next_? = queryNext("a[rel=next]:not([href=#])")
        Append(assets_?, next_?)
      }),
    archivePage("NX_TheDreamer", "The Dreamer", "http://www.thedreamercomic.com/read_pgmain.php",
      Shuffle.of(Selection.many(".act_wrap").focus {queryMixedArchive("h2, .flip_box_front .issue_title , .flip_box_back .issue_pages a")}){_.reverse},
      TransformUrls(queryImage("#comicnav > div.comicWrap > div.imageWrap > img"), List("\\.jpg.*" -> ".jpg"))),
    SimpleForward("NX_CheerImgur", "Cheer by Forview", "http://imgur.com/a/GTprX/",
      {
        Selection.unique("div.post-images").many("div.post-image-container").wrapEach { div =>
          extract(ImageRef(
            ref = s"http://i.imgur.com/${div.attr("id")}.png",
            origin = "http://imgur.com/a/GTprX/"))
        }
      })
  )

}
