package viscel.shared

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
}
