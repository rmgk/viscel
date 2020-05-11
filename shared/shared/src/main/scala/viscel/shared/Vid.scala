package viscel.shared

import scala.util.matching.Regex

final class Vid private(val str: String) extends AnyVal {
  override def toString: String = str
}

object Vid {



  val idregex: Regex = """^[\w-]+$""".r
  def from(str: String): Vid = {
    assert(idregex.unapplySeq(str).isDefined, s"Vid may only contain [\\w-], but was »$str«")
    new Vid(str)}
}
