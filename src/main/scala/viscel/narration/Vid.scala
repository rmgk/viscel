package viscel.narration

import java.io.{BufferedReader, InputStreamReader}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.stream.Stream

import org.scalactic.{Bad, ErrorMessage, Good, Or, attempt}
import viscel.narration.Queries._
import viscel.narration.interpretation.NarrationInterpretation.{AdditionalErrors, Append, NarratorADT, Shuffle, TransformUrls, Wrapper}
import viscel.scribe.{Link, Vurl}
import viscel.selection.Report
import viscel.shared.Log

import scala.annotation.tailrec
import scala.collection.JavaConverters.asScalaIteratorConverter
import scala.collection.immutable.Map
import scala.util.matching.Regex

object Vid {

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


  def makeNarrator(id: String, name: String, pos: Int, startUrl: Vurl, attrs: Map[String, Line], path: String): NarratorADT Or ErrorMessage = {
    val cid = generateID(id, name)
    type Wrap = Wrapper

    def has(keys: String*): Boolean = keys.forall(attrs.contains)

    def annotate(f: Wrapper, lines: Line*): Option[Wrap] = Some(AdditionalErrors(f, _.map(AdditionalPosition(lines, path))))

    val pageFun: Option[Wrap] = attrs match {
      case extract"image+next $img" => annotate(queryImageInAnchor(img.s), img)

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
        val replacements: List[(String, String)] = replacer.s.split("\\s+:::\\s+").sliding(2, 2).map { case Array(a, b) => (a, b) }.toList
        (pageFun.map(TransformUrls(_, replacements)), archFun.map(TransformUrls(_, replacements)))
      case _ => (pageFun, archFun)
    }

    val archFunRev = if (has("archiveReverse")) archFunReplace.map(Shuffle(_, chapterReverse)) else archFunReplace

    (pageFunReplace, archFunRev) match {
      case (Some(pf), None) => Good(NarratorADT(cid, name, Link(startUrl) :: Nil, pf))
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
    val preprocessed = lines.map(_.trim).zipWithIndex.map(p => Line(p._1, p._2 + 1)).filter(l => l.s.nonEmpty && !l.s.startsWith("--")).buffered

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

  def load(stream: Stream[String], path: String): List[Narrator] = {
    Log.Store.info(s"parsing definitions from $path")
    try parse(stream.iterator().asScala, path.toString) match {
      case Good(res) => res
      case Bad(err) =>
        Log.Store.warn(s"failed to parse $path errors: $err")
        Nil
    }
    finally stream.close()
  }

  def loadAll(dir: Path): List[Narrator] = {
    val dynamic = if (!Files.exists(dir)) Nil
    else {
      val paths = Files.newDirectoryStream(dir, "*.vid")
      paths.iterator().asScala.flatMap { path =>
        load(Files.lines(path, StandardCharsets.UTF_8), path.toString)
      }.toList
    }

    val stream = new BufferedReader(new InputStreamReader(getClass.getClassLoader.getResourceAsStream("definitions.vid"), StandardCharsets.UTF_8)).lines()
    val res = load(stream, "definitions.vid")
    (res ::: dynamic).reverse
  }

}
