package viscel.store.v4

import java.net.URL
import java.time.Instant

import cats.syntax.either._
import io.circe.export.Exported
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import io.circe.generic.extras.semiauto._
import io.circe.{Decoder, Encoder}
import viscel.store.v4.DataRow._

import scala.language.implicitConversions

final class Vurl private(private val uri: String) extends AnyVal {
  def uriString(): String = uri
  override def toString: String = s"Vurl($uriString)"
}

object Vurl {


  implicit val uriReader: Decoder[Vurl] = Decoder[String].map(Vurl.fromString)
  implicit val uriWriter: Encoder[Vurl] = Encoder[String].contramap[Vurl](_.uriString())

  def apply(s: String): Vurl = fromString(s)

  /* Ensure urls are always parsed. */
  implicit def fromString(uri: String): Vurl = {
    if (uri.startsWith("viscel:")) new Vurl(uri)
    else new Vurl(new URL(uri).toString)
  }
}


final case class DataRow(ref: Vurl,
                         loc: Option[Vurl] = None,
                         lastModified: Option[Instant] = None,
                         etag: Option[String] = None,
                         contents: List[Content]
                        )

object DataRow {
  sealed trait Content
  final case class Link(ref: Vurl, data: List[String] = Nil) extends Content
  final case class Blob(sha1: String, mime: String) extends Content
  final case class Chapter(name: String) extends Content
}

object V4Codecs {
  def makeIntellijBelieveTheImportIsUsed: Exported[Decoder[DataRow]] = exportDecoder[DataRow]

  implicit lazy val config: Configuration = Configuration.default.withDefaults

  implicit val instantEncoder: Encoder[Instant] = Encoder.encodeString.contramap[Instant](_.toString)
  implicit val instantDecoder: Decoder[Instant] = Decoder.decodeString.emap { str =>
    Either.catchNonFatal(Instant.parse(str)).leftMap(t => "Instant: " + t.getMessage)
  }

  implicit val dataRowEncoder: Encoder[DataRow] = deriveEncoder
  implicit val dataRowDecoder: Decoder[DataRow] = deriveDecoder

}
