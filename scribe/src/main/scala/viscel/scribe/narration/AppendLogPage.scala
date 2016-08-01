package viscel.scribe.narration

import java.net.URL
import java.time.Instant

import derive.key

sealed trait AppendLogEntry
@key("Page") case class AppendLogPage(
	/** reference that spawned this entry */
	ref: URL,
	/** location that was finally resolved and downloaded */
	loc: URL,
	contents: List[Story],
	date: Instant = Instant.now()
) extends AppendLogEntry
@key("Blob") case class AppendLogBlob(
	/** reference that spawned this entry */
	ref: URL,
	/** location that was finally resolved and downloaded */
	loc: URL,
	sha1: String,
	mime: String,
	date: Instant = Instant.now()
) extends AppendLogEntry



sealed trait Story
@key("Chapter") case class Chapter(name: String) extends Story
@key("Article") case class Article(blob: URL, origin: Option[URL] = None, data: List[String] = Nil) extends Story
@key("More") case class More(loc: URL, policy: Policy = Normal, data: List[String] = Nil) extends Story


sealed trait Policy {
	def ext: Option[Byte]
}
@key("Normal") case object Normal extends Policy {
	override def ext: Option[Byte] = None
}
@key("Volatile") case object Volatile extends Policy {
	override def ext: Option[Byte] = Some(0)
}

final case class Blob(sha1: String, mime: String)

