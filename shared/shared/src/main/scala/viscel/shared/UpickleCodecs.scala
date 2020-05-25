package viscel.shared

import java.time.Instant

import upickle.default._
import viscel.shared.Vid.from

object UpickleCodecs {
  implicit def vidRW: ReadWriter[Vid] = readwriter[String].bimap(_.str, from)
  implicit val DescriptionRW: ReadWriter[Description] = macroRW
  implicit val SharedImageRW: ReadWriter[SharedImage] = macroRW
  implicit val BlobRW       : ReadWriter[Blob]        = macroRW
  implicit val ChapterPosRW : ReadWriter[ChapterPos]  = macroRW
  implicit val ContentsRW   : ReadWriter[Contents]    = macroRW
  implicit val BookmarkRW   : ReadWriter[Bookmark]    = macroRW


  implicit val VurlRw: ReadWriter[Vurl] = readwriter[String].bimap(_.uriString(), Vurl.unsafeFromString)

  implicit val InstantRW: ReadWriter[Instant] =  readwriter[String].bimap(_.toString, Instant.parse)

  implicit val DataRowLinkRw: ReadWriter[DataRow.Link] = macroRW
  implicit val DataRowBlobRw: ReadWriter[DataRow.Blob] = macroRW
  implicit val DataRowChapterRw: ReadWriter[DataRow.Chapter] = macroRW
  implicit val DataRowContentRw: ReadWriter[DataRow.Content] = macroRW
  implicit val DataRowRw: ReadWriter[DataRow] = macroRW

}
