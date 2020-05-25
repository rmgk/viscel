package viscel.shared


import java.time.Instant

import cats.syntax.either._
import io.circe.export.Exported
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto.exportDecoder
import io.circe.generic.extras.semiauto.{deriveConfiguredCodec, deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.{Codec, Decoder, Encoder, KeyDecoder, KeyEncoder}

object CirceCodecs {

  implicit val vurlReader: Decoder[Vurl] = Decoder[String].map(Vurl.unsafeFromString)
  implicit val vurlWriter: Encoder[Vurl] = Encoder[String].contramap[Vurl](_.uriString())

  def makeIntellijBelieveTheImportIsUsed: Exported[Decoder[DataRow]] = exportDecoder[DataRow]

  implicit lazy val config: Configuration = Configuration.default.withDefaults

  implicit val instantEncoder: Encoder[Instant] = Encoder.encodeString.contramap[Instant](_.toString)
  implicit val instantDecoder: Decoder[Instant] = Decoder.decodeString.emap { str =>
    Either.catchNonFatal(Instant.parse(str)).leftMap(t => "Instant: " + t.getMessage)
  }

  implicit val linkCodec: Codec[DataRow.Link] = deriveConfiguredCodec

  implicit val dataRowEncoder: Encoder[DataRow] = deriveConfiguredEncoder
  implicit val dataRowDecoder: Decoder[DataRow] = deriveConfiguredDecoder

  implicit val dataRowContentCodec: Codec[DataRow.Content] = deriveConfiguredCodec
  implicit val dataRowCodec: Codec[DataRow] = Codec.from(dataRowDecoder, dataRowEncoder)



  implicit val vidR    : Decoder[Vid]    = Decoder.decodeString.map(Vid.from)
  implicit val vidKR   : KeyDecoder[Vid] = KeyDecoder.decodeKeyString.map(Vid.from)
  implicit val vidW    : Encoder[Vid]    = Encoder.encodeString.contramap[Vid](_.str)
  implicit val vidKW   : KeyEncoder[Vid] = KeyEncoder.encodeKeyString.contramap[Vid](_.str)
  implicit val vidcodec: Codec[Vid]      = Codec.from(vidR, vidW)

}
