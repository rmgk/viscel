package viscel.store

import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.StandardOpenOption._
import java.nio.file.{Files, Path}
import java.time.Instant

import cats.syntax.either._
import io.circe.export.Exported
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto.exportDecoder
import io.circe.generic.extras.semiauto.{deriveConfiguredCodec, deriveConfiguredDecoder, deriveConfiguredEncoder, deriveEnumerationCodec}
import io.circe.syntax._
import io.circe.{Codec, Decoder, Encoder, KeyDecoder, KeyEncoder}
import viscel.narration.FlowNarrator
import viscel.selektiv.FlowWrapper.{Extractor, Filter, Pipe, Plumbing, Restriction}
import viscel.shared.Vid
import viscel.store.v4.DataRow

import scala.jdk.CollectionConverters._

object CirceStorage {

  def store[T: Encoder](p: Path, data: T): Unit = synchronized {
    val jsonBytes = data.asJson.spaces2 :: Nil
    Files.createDirectories(p.getParent)
    Files.write(p, jsonBytes.asJava, UTF_8, CREATE, WRITE, TRUNCATE_EXISTING)
  }

  def load[T: Decoder](p: Path): Either[Exception, T] = synchronized {
    try {
      val jsonString = String.join("\n", Files.readAllLines(p, UTF_8))
      io.circe.parser.decode[T](jsonString)
    }
    catch {case e: IOException => Left(e)}
  }



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

  implicit val CRestriction : Codec[Restriction]  = deriveEnumerationCodec
  implicit val CExtractor   : Codec[Extractor]    = deriveEnumerationCodec
  implicit val CFilter      : Codec[Filter]       = deriveConfiguredCodec
  implicit val CPipe        : Codec[Pipe]         = deriveConfiguredCodec
  implicit val CPlumbing    : Codec[Plumbing]     = deriveConfiguredCodec

  implicit val CFlowNarrator: Codec[FlowNarrator] = deriveConfiguredCodec
}
