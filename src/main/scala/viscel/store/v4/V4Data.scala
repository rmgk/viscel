package viscel.store.v4

import java.time.Instant

import cats.syntax.either._
import io.circe.export.Exported
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import io.circe.generic.extras.semiauto._
import io.circe.{Decoder, Encoder}
import viscel.netzi.Vurl
import viscel.store.v4.DataRow._


final case class DataRow(ref: Vurl,
                         loc: Option[Vurl] = None,
                         lastModified: Option[Instant] = None,
                         etag: Option[String] = None,
                         contents: List[Content]
                        )

object DataRow {
  implicit def linkToLink(link: viscel.netzi.Link): Link = Link(link.ref, link.data)
  implicit def linkToLink2(link: Link): viscel.netzi.Link = viscel.netzi.Link(link.ref, link.data)
  sealed trait Content
  final case class Link(ref: Vurl, data: List[String] = Nil) extends Content
  final case class Blob(sha1: String, mime: String) extends Content
  final case class Chapter(name: String) extends Content
}

object V4Codecs {
  def makeIntellijBelieveTheImportIsUsed: Exported[Decoder[DataRow]] = exportDecoder[DataRow]


  implicit val uriReader: Decoder[Vurl] = Decoder[String].map(Vurl.fromString)
  implicit val uriWriter: Encoder[Vurl] = Encoder[String].contramap[Vurl](_.uriString())

  implicit lazy val config: Configuration = Configuration.default.withDefaults

  implicit val instantEncoder: Encoder[Instant] = Encoder.encodeString.contramap[Instant](_.toString)
  implicit val instantDecoder: Decoder[Instant] = Decoder.decodeString.emap { str =>
    Either.catchNonFatal(Instant.parse(str)).leftMap(t => "Instant: " + t.getMessage)
  }

  implicit val dataRowEncoder: Encoder[DataRow] = deriveEncoder
  implicit val dataRowDecoder: Decoder[DataRow] = deriveDecoder

}
