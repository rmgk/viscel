package viscel.narration

import java.nio.charset.StandardCharsets
import java.nio.file.Path

import better.files._
import org.scalactic.{Bad, ErrorMessage, Good, Or, attempt}
import viscel.narration.Narrator.Wrapper
import viscel.narration.Queries._
import viscel.netzi.{Report, Vurl}
import viscel.netzi.Narration.{AdditionalErrors, Alternative, Append, Constant, LocationMatch, MapW}
import viscel.shared.{Log, Vid}
import viscel.store.v4.DataRow

import scala.annotation.tailrec
import scala.collection.immutable.Map
import scala.util.matching.Regex

object ViscelDefinition {

  case class Line(s: String, p: Int)
  type It = BufferedIterator[Line]

  val extractIDAndName: Regex = """^-(\w*):(.+)$""".r
  val extractAttribute: Regex = """^:(\S+)\s*(.*)$""".r

  def parseURL(it: It): Vurl Or ErrorMessage = {
    val Line(url, pos) = it.next()
    attempt(Vurl.fromString(url)).badMap(_ => s"malformed URL at line $pos: $url")
  }

  val attributeReplacements = Map(
    "ia" -> "image+next",
    "i" -> "image",
    "is" -> "images",
    "n" -> "next",
    "am" -> "mixedArchive",
    "ac" -> "chapterArchive",
    )
  def normalizeAttributeName(att: String): String = {
    attributeReplacements.getOrElse(att, att)
  }

  @tailrec
  def parseAttributes(it: It, acc: Map[String, Line]): Map[String, Line] =
    if (!it.hasNext) acc
    else it.head match {
      case Line(extractAttribute(name, value), pos) =>
        it.next()
        parseAttributes(it, acc.updated(normalizeAttributeName(name), Line(value, pos)))
      case _ => acc
    }


  implicit class ExtractContext(val sc: StringContext) {
    object extract {
      def unapplySeq[T](m: Map[String, T]): Option[Seq[T]] = {
        val keys = sc.parts.map(_.trim).filter(_.nonEmpty)
        val res = keys.flatMap(m.get)
        if (res.lengthCompare(keys.size) == 0) Some(res)
        else None
      }
    }
  }

  case class AdditionalPosition(lines: Seq[Line], path: String)(annotated: Report) extends Report {
    override def describe: String = s"${annotated.describe} in $path lines [${lines.map(_.p).mkString(", ")}]"
  }

  private def generateID(id: String, name: String) =
    if (id.matches("""^\w+\_.\w+""")) id
    else "VD_" + (
      if (id.nonEmpty) id
      else name.replaceAll("\\s+", "").replaceAll("\\W", "_"))

  def transformUrls(replacements: List[(String, String)])(stories: List[DataRow.Content]): List[DataRow.Content] = {

    def replaceVurl(url: Vurl): Vurl =
      replacements.foldLeft(url.uriString) {
        case (u, (matches, replace)) => u.replaceAll(matches, replace)
      }

    stories.map {
      case DataRow.Link(url, data)     => DataRow.Link(replaceVurl(url), data)
      case o                           => o
    }
  }

  def TransformUrls(target: Wrapper, replacements: List[(String, String)]) =
    MapW(target, transformUrls(replacements))

  def makeNarrator(id: String, name: String, pos: Int, startUrl: Vurl, attrs: Map[String, Line], path: String): NarratorADT Or ErrorMessage = {
    val cid = generateID(id, name)
    type Wrap = Wrapper

    def has(keys: String*): Boolean = keys.forall(attrs.contains)

    def annotate(f: Wrapper, lines: Line*): Option[Wrap] = Some(AdditionalErrors(f, _.map(AdditionalPosition(lines, path))))

    val pageFun: Option[Wrap] = attrs match {
      case extract"image+next $img" => annotate(queryImageInAnchor(img.s), img)

      case extract"image $img next $next skip $skip" => annotate(
        Append(Alternative(queryImage(img.s),
                           LocationMatch(skip.s.r,
                                         Constant(Nil),
                                         queryImage(img.s))),
               queryNext(next.s)), img, next)

      case extract"image $img next $next" => annotate(queryImageNext(img.s, next.s), img, next)

      case extract"images $img next $next" => annotate(Append(queryImages(img.s), queryNext(next.s)), img, next)

      case extract"image $img" => annotate(queryImage(img.s), img)
      case extract"images $img" => annotate(queryImages(img.s), img)
      case _ => None
    }

    val archFun: Option[Wrap] = attrs match {
      case extract"mixedArchive $arch" => annotate(queryMixedArchive(arch.s), arch)

      case extract"chapterArchive $arch" => annotate(queryChapterArchive(arch.s), arch)

      case _ => None
    }

    val (pageFunReplace, archFunReplace) = attrs match {
      case extract"url_replace $replacer" =>
        val replacements: List[(String, String)] =
          replacer.s.split("\\s+:::\\s+").sliding(2, 2).map { case Array(a, b) => (a, b) }.toList
        (pageFun.map(TransformUrls(_, replacements)), archFun.map(TransformUrls(_, replacements)))
      case _ => (pageFun, archFun)
    }

    val archFunRev = if (has("archiveReverse")) archFunReplace.map(MapW(_, chapterReverse)) else archFunReplace

    (pageFunReplace, archFunRev) match {
      case (Some(pf), None) => Good(NarratorADT(Vid.from(cid), name, DataRow.Link(startUrl) :: Nil, pf))
      case (Some(pf), Some(af)) => Good(Templates.archivePage(cid, name, startUrl, af, pf))
      case _ => Bad(s"invalid combinations of attributes for $cid at line $pos")
    }

  }


  def parseNarration(it: It, path: String): Narrator Or ErrorMessage = {
    it.next() match {
      case Line(extractIDAndName(id, name), pos) =>
        parseURL(it).flatMap { url =>
          val attrs = parseAttributes(it, Map())
          makeNarrator(id, name, pos, url, attrs, path)
        }

      case Line(line, pos) => Bad(s"expected definition at line $pos, but found $line")
    }
  }

  def parse(lines: Iterator[String], path: String): List[Narrator] Or ErrorMessage = {
    val preprocessed = lines.map(_.trim)
                       .zipWithIndex.map(p => Line(p._1, p._2 + 1))
                       .filter(l => l.s.nonEmpty && !l.s.startsWith("--"))
                       .buffered

    def go(it: It, acc: List[Narrator]): List[Narrator] Or ErrorMessage =
      if (!it.hasNext) {
        Good(acc)
      }
      else {
        parseNarration(it, path) match {
          case Good(n) => go(it, n :: acc)
          case Bad(e) => Bad(e)
        }
      }

    go(preprocessed, Nil)
  }

  def load(stream: Iterator[String], path: String): List[Narrator] = {
    Log.Store.info(s"parsing definitions from $path")
    parse(stream, path.toString) match {
      case Good(res) => res
      case Bad(err) =>
        Log.Store.warn(s"failed to parse $path errors: $err")
        Nil
    }
  }

  def loadAll(dir: Path): List[Narrator] = {
    val defdir = File(dir)

    val res = if (!defdir.exists) Nil
    else {
      val paths = defdir.glob("*.vid").toList
      paths.flatMap { path =>
        load(path.lines(StandardCharsets.UTF_8).toArray.iterator, path.pathAsString)
      }
    }
    Log.Narrate.info(s"Found ${res.size} definitions in $defdir.")
    res
  }

}
