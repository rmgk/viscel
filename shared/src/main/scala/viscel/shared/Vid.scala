package viscel.shared

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

import scala.util.matching.Regex

final class Vid private(val str: String) extends AnyVal {
  override def toString: String = str
}

object Vid {
  val idregex: Regex = """^[\w-]+$""".r
  def from(str: String): Vid = {
    assert(idregex.unapplySeq(str).isDefined, s"Vid may only contain [\\w-], but was $str")
    new Vid(str)}
  implicit def vidR: Decoder[Vid] = Decoder.decodeString.map(new Vid(_))
  implicit def vidKR: KeyDecoder[Vid] = KeyDecoder.decodeKeyString.map(new Vid(_))
  implicit def vidW: Encoder[Vid] = Encoder.encodeString.contramap[Vid](_.str)
  implicit def vidKW: KeyEncoder[Vid] = KeyEncoder.encodeKeyString.contramap[Vid](_.str)
}
