package viscel.store

import java.time.Instant

import cats.syntax.either._
import io.circe.export.Exported
import io.circe.generic.extras._
import io.circe.generic.extras.auto._
import io.circe.{Decoder, Encoder}
import viscel.shared.Blob

/** Single row in a [[StoreManager]]. Is either a [[PageData]] or a [[BlobData]]. */
sealed trait ScribeDataRow {
  /** reference that spawned this entry */
  def ref: Vurl
  def matchesRef(o: ScribeDataRow): Boolean = ref == o.ref
  /** Basically equals, but ignoring date */
  def differentContent(o: ScribeDataRow): Boolean = (this, o) match {
    case (PageData(ref1, loc1, _, contents1), PageData(ref2, loc2, _, contents2)) =>
      !(ref1 == ref2 && loc1 == loc2 && contents1 == contents2)
    case (BlobData(ref1, loc1, _, blob1), BlobData(ref2, loc2, _, blob2)) =>
      !(ref1 == ref2 && loc1 == loc2 && blob1 == blob2)
    case _ => true
  }
}

/** A web page parsed and stored in [[StoreManager]] and [[Book]]
  *
  * @param ref      reference that spawned this entry
  * @param loc      location that was finally resolved and downloaded
  * @param date     last modified timestamp when available, current date otherwise
  * @param contents links and images found on this page
  */
/*@key("Page")*/ case class PageData(ref: Vurl,
                                     loc: Vurl,
                                     date: Instant,
                                     contents: List[WebContent]
                                    ) extends ScribeDataRow {
  def articleCount: Int = contents.count(_.isInstanceOf[ImageRef])
}

/** A reference to a binary object stored in [[StoreManager]] and [[Book]]
  *
  * @param ref  reference that spawned this entry, linked to [[ImageRef.ref]]
  * @param loc  location that was finally resolved and downloaded
  * @param date last modified timestamp when available, current date otherwise
  * @param blob reference to the file
  */
/*@key("Blob")*/ case class BlobData(ref: Vurl,
                                     loc: Vurl,
                                     date: Instant,
                                     blob: Blob
                                    ) extends ScribeDataRow

/** The things a person is interested in [[Chapter]] and [[Article]], content of [[ScribeDataRow]] */
sealed trait ReadableContent

/** Aggregate the [[article]] and the [[blob]], returned  */
case class Article(article: ImageRef, blob: Option[Blob]) extends ReadableContent

/** Result of parsing web pages by [[viscel.narration.Narrator]] */
sealed trait WebContent
/** A chapter named [[name]] */
/*@key("Chapter")*/ case class Chapter(name: String) extends WebContent with ReadableContent
/** A reference to an image or similar at url [[ref]] (referring to [[BlobData.ref]])
  * and originating at [[origin]] (referring to [[PageData.ref]]))
  * with additional [[data]] such as HMTL attributes. */
/*@key("Article")*/ case class ImageRef(ref: Vurl, origin: Vurl, data: Map[String, String] = Map()) extends WebContent
/** [[Link.ref]] to another [[PageData]], with an update [[policy]], and narator specific [[data]]. */
/*@key("Link")*/ case class Link(ref: Vurl, policy: Policy = Normal, data: List[String] = Nil) extends WebContent

/** The update [[Policy]] decides if [[viscel.crawl.Crawl]] checks for updates [[Volatile]] or not [[Normal]] */
sealed trait Policy
/*@key("Normal")*/ case object Normal extends Policy
/*@key("Volatile")*/ case object Volatile extends Policy


/** Pickler customization for compatibility */
object CustomPicklers {
  def makeIntellijBelieveTheImportIsUsed: Exported[Decoder[Policy]] = exportDecoder[Policy]

  val blobName: String = classOf[BlobData].getSimpleName
  val pageName: String = classOf[PageData].getSimpleName

  /** use "\$type" field in json to detect type,
    * was upickle default and is used by every [[Book]] ... */
  implicit val config: Configuration = Configuration.default.withDefaults
                                       .withDiscriminator("$" + "type")
  .copy(transformConstructorNames = {
    case `blobName` => "Blob"
    case `pageName` => "Page"
    case other => other
  })


  /** allow "Article" as an [[ImageRef]] in the serialized format */
  implicit val webContentReader: Decoder[WebContent] = semiauto.deriveDecoder[WebContent].prepare { cursor =>
    val t = cursor.downField("$type")
    t.as[String] match {
      case Right("Article") => t.set(io.circe.Json.fromString("ImageRef")).up
      case _ => cursor
    }
  }
  /** note: new [[ImageRef]] are written as [[ImageRef]] **/
  implicit val webContentWriter: Encoder[WebContent] = semiauto.deriveEncoder[WebContent]


  /** rename [[BlobData]] and [[PageData]] to just "Page" and "Blob" in the serialized format */
  implicit val appendlogReader: Decoder[ScribeDataRow] = semiauto.deriveDecoder[ScribeDataRow]
  implicit val appendLogWriter: Encoder[ScribeDataRow] = semiauto.deriveEncoder[ScribeDataRow]


  /** coding for instants saved to the database */
  implicit val instantWriter: Encoder[Instant] = Encoder.encodeString.contramap[Instant](_.toString)
  implicit val instantReader: Decoder[Instant] = Decoder.decodeString.emap { str =>
    Either.catchNonFatal(Instant.parse(str)).leftMap(t => "Instant: " + t.getMessage)
  }

}
