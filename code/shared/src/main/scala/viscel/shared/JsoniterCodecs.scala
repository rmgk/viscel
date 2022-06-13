package viscel.shared

import com.github.plokhotnyuk.jsoniter_scala.core._
import com.github.plokhotnyuk.jsoniter_scala.macros._

import scala.annotation.nowarn

object JsoniterCodecs {
  def writeString[T: JsonValueCodec](value: T) = writeToString(value)
  def writeArray[T: JsonValueCodec](value: T)  = writeToArray(value)

  def readString[T: JsonValueCodec](str: String) = readFromString[T](str)

  implicit val StringRw: JsonValueCodec[String] = JsonCodecMaker.make

  implicit val vidRW: JsonValueCodec[Vid] = new JsonValueCodec[Vid] {
    override def decodeValue(in: JsonReader, default: Vid): Vid = {
      val str = in.readString(null)
      if (str == null) in.decodeError("reading Vid failed")
      try Vid.from(str)
      catch {
        case assertionError: AssertionError =>
          in.decodeError(assertionError.getMessage)
      }
    }
    override def encodeValue(x: Vid, out: JsonWriter): Unit = out.writeVal(x.str)
    override def nullValue: Vid                             = null.asInstanceOf[Vid]
  }

  implicit val vidKey: JsonKeyCodec[Vid] = new JsonKeyCodec[Vid] {
    override def decodeKey(in: JsonReader): Vid           = Vid.from(in.readKeyAsString())
    override def encodeKey(x: Vid, out: JsonWriter): Unit = out.writeKey(x.str)
  }

  // implicit val DescriptionRW: JsonValueCodec[Description] = JsonCodecMaker.make
  // implicit val SharedImageRW: JsonValueCodec[SharedImage] = JsonCodecMaker.make
  // implicit val BlobRW       : JsonValueCodec[Blob]        = JsonCodecMaker.make
  // implicit val ChapterPosRW : JsonValueCodec[ChapterPos]  = JsonCodecMaker.make
  implicit val ContentsRW: JsonValueCodec[Contents] = JsonCodecMaker.make
  // implicit val BookmarkRW   : JsonValueCodec[Bookmark]    = JsonCodecMaker.make

  implicit def OptionCodec[T: JsonValueCodec]: JsonValueCodec[Option[T]] = JsonCodecMaker.make
  implicit val HintCodec: JsonValueCodec[(Vid, Boolean)]                 = JsonCodecMaker.make

  implicit val VurlRw: JsonValueCodec[Vurl] = new JsonValueCodec[Vurl] {
    override def decodeValue(in: JsonReader, default: Vurl): Vurl = Vurl.unsafeFromString(in.readString(""))
    override def encodeValue(x: Vurl, out: JsonWriter): Unit      = out.writeVal(x.uriString())
    override def nullValue: Vurl                                  = null.asInstanceOf[Vurl]
  }

  implicit val DataRowRw: JsonValueCodec[DataRow] =
    JsonCodecMaker.make(CodecMakerConfig.withDiscriminatorFieldName(None))
  implicit val DataRowListRw: JsonValueCodec[List[DataRow]] = JsonCodecMaker.make

  implicit val MapVidLongCodec: JsonValueCodec[Map[Vid, Long]]               = JsonCodecMaker.make
  implicit val MapVidDescriptionCodec: JsonValueCodec[Map[Vid, Description]] = JsonCodecMaker.make
  implicit val MapVidBookmarkCodec: JsonValueCodec[Map[Vid, Bookmark]]       = JsonCodecMaker.make

  implicit val CookieMapCodec: JsonValueCodec[Map[String, String]] = JsonCodecMaker.make

}
