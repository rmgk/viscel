package viscel.narration.narrators

import org.scalactic.Good
import viscel.narration.interpretation.NarrationInterpretation.{Combine, Constant, Decision, NarratorADT, Shuffle}
import viscel.narration.{Narrator, Queries}
import viscel.scribe.{ImageRef, Link, Volatile, Vurl}
import viscel.selection.{ReportTools, Selection}

import scala.collection.immutable.Set


object KatBox {

  val cores: Set[Narrator] = Set[(String, String, Option[Vurl])](
    ("addictivescience", "Addictive Science", None),
    ("ai", "Artificial Incident", None),
    ("cblue", "Caribbean Blue", None),
    ("desertfox", "Desert Fox", None),
    ("dmfa", "DMFA", None),
    ("draconia", "Draconia Chronicles", None),
    //("falsestart", "False Start", None), // requires age check
    ("iba", "Itsy Bitsy Adventures", None),
    ("imew", "iMew", None),
    ("laslindas", "Las Lindas!", Some[Vurl]("http://laslindas.katbox.net/las-lindas/")),
    ("rixie", "Debunkers", None),
    ("oasis", "Oasis", None),
    ("ourworld", "Our World", None),
    ("paprika", "Paprika", None),
    ("peterandcompany", "Peter and Company", None),
    ("peterandwhitney", "Peter and Whitney", None),
    ("pmp", "Practice Makes Perfect", None),
    ("rascals", "Rascals", None),
    ("theeye", "The Eye of Ramalach", None),
    ("tinaofthesouth", "Tina of the South", None),
    ("uberquest", "Uber Quest", None),
    ("yosh", "Yosh!", None),
  ).map { case (_id, _name, _url) =>
    NarratorADT(s"KatBox_${_id}", _name, List(Link(_url.getOrElse(s"http://${_id}.katbox.net/archive/"), Volatile)),
      Shuffle.of(Selection.many("span.archive-link a.webcomic-link").focus {
        // laslindas at least seems to miss some pages, just skip them
        Decision(_.childNodeSize() == 0, Constant(Good(Nil)), {

          val vurl_? = Selection.wrapOne(Queries.extractURL)
          val img_? = Selection.unique("img").wrapOne(i => ReportTools.extract(i.absUrl("src")))
          Combine.of(img_?, vurl_?) { (img, vurl) =>
            List(ImageRef(
              ref = img.replaceFirst("-\\d+x\\d+\\.", "."),
              origin = vurl,
              data = Map()))
          }
        })
      })(_.reverse)
    )
  }
}
