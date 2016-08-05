package viscel.scribe

import java.time.Instant

import derive.key
import upickle.default.{ReadWriter, Reader, Writer}
import viscel.shared.Blob


sealed trait ScribeDataRow {
	/** reference that spawned this entry */
	def ref: Vurl
	def matches(o: ScribeDataRow): Boolean = ref.equals(o.ref)
}
@key("Page") case class ScribePage(
	/* reference that spawned this entry */
	ref: Vurl,
	/* location that was finally resolved and downloaded */
	loc: Vurl,
	date: Instant,
	contents: List[WebContent]
) extends ScribeDataRow
@key("Blob") case class ScribeBlob(
	/* reference that spawned this entry */
	ref: Vurl,
	/* location that was finally resolved and downloaded */
	loc: Vurl,
	date: Instant,
	blob: Blob
) extends ScribeDataRow


sealed trait ReadableContent
case class Article(article: ArticleRef, blob: Option[Blob]) extends ReadableContent

sealed trait WebContent
@key("Chapter") case class Chapter(name: String) extends WebContent with ReadableContent
@key("Article") case class ArticleRef(ref: Vurl, origin: Vurl, data: Map[String, String] = Map()) extends WebContent
@key("Link") case class Link(ref: Vurl, policy: Policy = Normal, data: List[String] = Nil) extends WebContent


sealed trait Policy
@key("Normal") case object Normal extends Policy
@key("Volatile") case object Volatile extends Policy


object ScribePicklers {
	implicit val appendlogReader: ReadWriter[ScribeDataRow] = upickle.default.macroRW[ScribeDataRow]

	implicit val instantWriter: Writer[Instant] = Writer[Instant] { instant =>
		upickle.Js.Str(instant.toString)
	}

	implicit val instantReader: Reader[Instant] = Reader[Instant] {
		case upickle.Js.Str(str) => Instant.parse(str)
	}

}
