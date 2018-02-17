package viscel.vitzen

import java.io.File
import java.nio.file.Path
import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import java.time.{DateTimeException, LocalDate, LocalDateTime, LocalTime}
import java.util

import org.asciidoctor.ast.{Document, DocumentHeader, Title}
import org.asciidoctor.{AsciiDocDirectoryWalker, Asciidoctor, OptionsBuilder, SafeMode}

import scala.collection.JavaConverters._

object Helper {
  val timeFormatter: DateTimeFormatter = new DateTimeFormatterBuilder()
    .parseCaseInsensitive()
    .append(DateTimeFormatter.ISO_LOCAL_DATE)
    .optionalStart()
    .optionalStart().appendLiteral('T').optionalEnd()
    .optionalStart().appendLiteral(' ').optionalEnd()
    .append(DateTimeFormatter.ISO_LOCAL_TIME)
    .optionalEnd()
    .optionalStart().appendOffsetId().optionalEnd()
    .toFormatter()

  def parseDate(dateString: String): LocalDateTime = {
    if (dateString == null) return LocalDateTime.MIN
    val temporal = Helper.timeFormatter.parse(dateString)
    val ldate = try {LocalDate.from(temporal)} catch {case _: DateTimeException => LocalDate.MIN}
    val ltime = try {LocalTime.from(temporal)} catch {case _: DateTimeException => LocalTime.MIN}
    LocalDateTime.of(ldate, ltime)
  }

}

class AsciiData(asciidoctor: Asciidoctor, basedir: Path) {

  val options: util.Map[String, AnyRef] = OptionsBuilder.options().headerFooter(false).safe(SafeMode.SERVER).asMap()

  def getOne(pathString: String): Post = {
    val path = basedir.resolve(pathString)
    makePost(path)
  }


  private def makePost(path: Path) = {
    val document = asciidoctor.loadFile(path.toFile, options)
    val opts = new util.HashMap[AnyRef, AnyRef]
    opts.put("partition", boolean2Boolean(true))

    val header = DocumentHeader.createDocumentHeader(document.doctitle(opts).asInstanceOf[Title],
      document.getDocumentRuby.getTitle,
      document.getDocumentRuby.getAttributes)
    new Post(basedir.relativize(path), header, document)
  }


  def getAll(): List[Post] = {
    val all = new AsciiDocDirectoryWalker(basedir.toString).scan().iterator().asScala
      .map { (f: File) => makePost(f.toPath) }.toList

    all
  }
}


class Post(val path: Path, val header: DocumentHeader, val document: Document) {
  def categories(): List[String] = header.getAttributes.getOrDefault("categories","").toString.split(',').map(_.trim)(collection.breakOut)

  def summary(): String = Option(document.getBlocks.get(0)).fold("")(b => b.convert())

  def title: String = header.getDocumentTitle.getCombined
  lazy val date: LocalDateTime = Helper.parseDate(header.getRevisionInfo.getDate)
  def content: String = document.convert()
  lazy val modified: Option[LocalDateTime] = Option(header.getAttributes.get("modified")).map(m => Helper.parseDate(m.toString))
}
