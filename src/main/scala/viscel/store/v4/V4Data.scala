package viscel.store.v4

import java.time.Instant

import cats.syntax.either._
import io.circe.export.Exported
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import io.circe.generic.extras.semiauto._
import io.circe.{Decoder, Encoder}
import viscel.store.Vurl
import viscel.store.v4.DataRow._


final case class DataRow(ref: Vurl,
                         loc: Option[Vurl] = None,
                         lastModified: Option[Instant] = None,
                         etag: Option[String] = None,
                         contents: List[Content]
                        )

object DataRow {
  sealed trait Content
  final case class Link(ref: Vurl, data: List[String] = Nil) extends Content {
    lazy val dataMap: Map[String, String] = data.sliding(2, 2).filter(_.size == 2).map {
      case List(a, b) => a -> b
    }.toMap
  }
  final case class Blob(sha1: String, mime: String) extends Content
  final case class Chapter(name: String) extends Content

  def ImageRef(ref: Vurl, data: Map[String, String] = Map()): DataRow.Link = {
    Link(ref, data.flatMap(f => List(f._1, f._2)).toList)
  }
}

object V4Codecs {
  def makeIntellijBelieveTheImportIsUsed: Exported[Decoder[DataRow]] = exportDecoder[DataRow]

  implicit lazy val config: Configuration = Configuration.default.withDefaults

  implicit val instantEncoder: Encoder[Instant] = Encoder.encodeString.contramap[Instant](_.toString)
  implicit val instantDecoder: Decoder[Instant] = Decoder.decodeString.emap { str =>
    Either.catchNonFatal(Instant.parse(str)).leftMap(t => "Instant: " + t.getMessage)
  }

  implicit val linkEncoder: Encoder[Link] = deriveEncoder
  implicit val linkDecoder: Decoder[Link] = deriveDecoder

  implicit val blobEncoder: Encoder[Blob] = deriveEncoder
  implicit val blobDecoder: Decoder[Blob] = deriveDecoder

  implicit val chapterEncoder: Encoder[Chapter] = deriveEncoder
  implicit val chapterDecoder: Decoder[Chapter] = deriveDecoder

  implicit val dataRowEncoder: Encoder[DataRow] = deriveEncoder
  implicit val dataRowDecoder: Decoder[DataRow] = deriveDecoder

}
