package viscel.narration.narrators

import viscel.narration.{Narrator, NarratorADT, Queries}
import viscel.selektiv.Narration.{Combination, Condition, Constant, ElementW, MapW}
import viscel.selektiv.{ReportTools, Selection}
import viscel.shared.Vid
import viscel.store.v3.Volatile
import viscel.store.v4.{DataRow, Vurl}

import scala.collection.immutable.Set

// also dead for the time beeing
object KatBox {

  val cores: Set[Narrator] = Set[(String, String, Option[Vurl])](
    ("desertfox", "Desert Fox", None),
    ("dmfa", "DMFA", None),
    ("draconia", "Draconia Chronicles", None),
    //("falsestart", "False Start", None), // requires age check
    ("iba", "Itsy Bitsy Adventures", None),
    ("rixie", "Debunkers", None),
    ("oasis", "Oasis", None),
    ("ourworld", "Our World", None),
    ("peterandcompany", "Peter and Company", None),
    ("peterandwhitney", "Peter and Whitney", None),
    ("pmp", "Practice Makes Perfect", None),
    ("uberquest", "Uber Quest", None),
  ).map { case (_id, _name, _url) =>
    NarratorADT(Vid.from(s"KatBox_${_id}"), _name,
                List(DataRow.Link(_url.getOrElse(s"http://${_id}.katbox.net/archive/"), List(Volatile.toString))),
                MapW.reverse(Selection.many("span.archive-link a.webcomic-link").focus {
        // laslindas at least seems to miss some pages, just skip them
                  Condition(ElementW.map{_.childNodeSize() == 0}, Constant(Nil), {

          val vurl_? = Selection.wrapOne(Queries.extractURL)
          // not awabanner2015 is a workaround for the rascals archives
          val img_? = Selection.unique("img:not([src~=awabanner2015])").wrapOne(i => ReportTools.extract(i.absUrl("src")))
          Combination.of(img_?, vurl_?) { (img, vurl) =>
            List(DataRow.Link(img.replaceFirst("-\\d+x\\d+\\.", ".")))
          }
        })
      })
    )
  }
}
