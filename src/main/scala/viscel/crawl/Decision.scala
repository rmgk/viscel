package viscel.crawl

import viscel.scribe.{ImageRef, Link}

sealed trait Decision
object Decision {
  case class ImageD(img: ImageRef) extends Decision
  case class LinkD(link: Link) extends Decision
  case object Done extends Decision
}
