package viscel.narration

import java.net.MalformedURLException
import java.nio.charset.StandardCharsets
import java.nio.file.Path

import better.files._
import viscel.crawl.Decider
import viscel.selektiv.FlowWrapper._
import viscel.selektiv.{FlowWrapper, Report}
import viscel.shared.{Log, Vid}
import viscel.store.CirceStorage
import viscel.store.v4.{DataRow, Vurl}

import scala.annotation.tailrec
import scala.collection.immutable.Map
import scala.util.matching.Regex

object ViscelDefinition {

  case class Line(s: String, p: Int)
  type It = scala.collection.BufferedIterator[Line]
  type ErrorMessage = String
  type Or[T, V] = Either[V, T]

  val extractIDAndName: Regex = """^-(\w*):(.+)$""".r
  val extractAttribute: Regex = """^:(\S+)\s*(.*)$""".r

  def parseURL(it: It): Vurl Or ErrorMessage = {
    val Line(url, pos) = it.next()
    try Right(Vurl.fromString(url))
    catch {
      case mue: MalformedURLException =>
        Left(s"malformed URL at line $pos: $url")
    }
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
      case _                                        => acc
    }


  implicit class ExtractContext(val sc: StringContext) {
    object extract {
      def unapplySeq[T](m: Map[String, T]): Option[Seq[T]] = {
        val keys = sc.parts.map(_.trim).filter(_.nonEmpty)
        val res  = keys.flatMap(m.get)
        if (res.lengthCompare(keys.size) == 0) Some(res)
        else None
      }
    }
  }

  case class AdditionalPosition(pos: Int, path: String)(annotated: Report) extends Report {
    override def describe: String = s"${annotated.describe} in $path lines [${pos}]"
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
      case DataRow.Link(url, data) => DataRow.Link(replaceVurl(url), data)
      case o                       => o
    }
  }

  def makeNarrator(id: String, name: String, pos: Int, startUrl: Vurl, attrs: Map[String, Line], path: String): FlowNarrator = {
    val cid = generateID(id, name)

    val imageNextPipe = attrs.get("image+next").map { img =>
      FlowWrapper.Pipe(img.s, Restriction.Unique,
                       List(FlowWrapper.Extractor.Image, Extractor.OptionalParentMore))
    }

    val imagePipe = None.orElse(attrs.get("image").map(_ -> Restriction.Unique))
                        .orElse(attrs.get("images").map(_ -> Restriction.NonEmpty))
                        .orElse(attrs.get("images?").map(_ -> Restriction.None))
                        .map { case (img, res) =>
                          FlowWrapper.Pipe(img.s, res, List(FlowWrapper.Extractor.Image))
                        }

    val nextPipe = attrs.get("next").map { next =>
      Pipe(next.s, Restriction.None, List(FlowWrapper.Extractor.More), filter = List(Filter.SelectSingleNext))
    }

    val archFunRev =
      (if (attrs.contains("archiveReverse")) Some(false)
       else if (attrs.contains("archiveReverseFull")) Some(true) else None).map { reverseInner =>
        Filter.ChapterReverse(reverseInner)
      }.toList

    val mixedArchivePipe = attrs.get("mixedArchive").map { arch =>
      Pipe(arch.s, Restriction.NonEmpty, List(FlowWrapper.Extractor.MixedArchive),
           filter = archFunRev,
           conditions = List(startUrl.uriString()))
    }

    val chapterArchivePipe = attrs.get("chapterArchive").map { arch =>
      Pipe(arch.s, Restriction.NonEmpty, List(FlowWrapper.Extractor.Chapter, Extractor.More),
           filter = archFunRev,
           conditions = List(startUrl.uriString()))
    }


    val transformFun = attrs.get("url_replace") map { replacer =>
      val replacements: List[(String, String)] =
        replacer.s.split("\\s+:::\\s+").sliding(2, 2).map { case Array(a, b) => (a, b) }.toList
      Filter.TransformUrls(replacements)
    }

    val pipes = List(imageNextPipe, imagePipe, nextPipe, mixedArchivePipe, chapterArchivePipe).flatten

    val transformedPipes = transformFun match {
      case None            => pipes
      case Some(transform) =>
        pipes.map { pipe =>
          pipe.copy(filter = transform :: pipe.filter)
        }
    }

    val plumbing = Plumbing(transformedPipes)

    FlowNarrator(Vid.from(cid), name, DataRow.Link(startUrl, if (chapterArchivePipe.isDefined || mixedArchivePipe.isDefined) List(Decider.Volatile) else Nil) :: Nil, plumbing)

  }


  def parseNarration(it: It, path: String): FlowNarrator Or ErrorMessage = {
    it.next() match {
      case Line(extractIDAndName(id, name), pos) =>
        parseURL(it).map { url =>
          val attrs = parseAttributes(it, Map())
          makeNarrator(id, name, pos, url, attrs, path)
        }

      case Line(line, pos) => Left(s"expected definition at line $pos, but found $line")
    }
  }

  def parse(lines: Iterator[String], path: String): List[FlowNarrator] Or ErrorMessage = {
    val preprocessed = lines.map(_.trim)
                            .zipWithIndex.map(p => Line(p._1, p._2 + 1))
                            .filter(l => l.s.nonEmpty && !l.s.startsWith("--"))
                            .buffered

    def go(it: It, acc: List[FlowNarrator]): List[FlowNarrator] Or ErrorMessage =
      if (!it.hasNext) {
        Right(acc)
      }
      else {
        parseNarration(it, path) match {
          case Right(n) => go(it, n :: acc)
          case Left(e)  => Left(e)
        }
      }

    go(preprocessed, Nil)
  }

  def load(stream: Iterator[String], path: String): List[FlowNarrator] = {
    Log.Store.info(s"parsing definitions from $path")
    parse(stream, path.toString) match {
      case Right(res) => res
      case Left(err)  =>
        Log.Store.warn(s"failed to parse $path errors: $err")
        Nil
    }
  }

  def loadAll(dir: Path): List[FlowNarrator] = {
    val defdir = File(dir)

    val res = if (!defdir.exists) Nil
              else {
                val paths = defdir.glob("*.vid").toList
                paths.flatMap { path =>
                  load(path.lines(StandardCharsets.UTF_8).toArray.iterator, path.pathAsString)
                }
              }
    Log.Narrate.info(s"Found ${res.size} definitions in $defdir.")
    import CirceStorage.CFlowNarrator
    CirceStorage.store(defdir./("flowdefs.json").path, res)
    res
  }

}
