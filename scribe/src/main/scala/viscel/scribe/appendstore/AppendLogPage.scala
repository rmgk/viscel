package viscel.scribe.appendstore

import java.net.URL
import java.time.Instant

import derive.key
import viscel.scribe.narration.{Normal, Policy}

sealed trait AppendLogEntry
@key("Page") case class AppendLogPage(
	/** location that spawned this entry */
	initialLocation: URL,
	/** location that was finally resolved and downloaded */
	resolvedLocation: URL,
	contents: List[AppendLogElements],
	date: Instant = Instant.now()
) extends AppendLogEntry
@key("Blob") case class AppendLogBlob(
	/** location that spawned this entry */
	initialLocation: URL,
	/** location that was finally resolved and downloaded */
	resolvedLocation: URL,
	sha1: String,
	mime: String,
	date: Instant = Instant.now()
) extends AppendLogEntry

sealed trait AppendLogElements
@key("Chapter") case class AppendLogChapter(name: String) extends AppendLogElements
@key("Article") case class AppendLogArticle(blob: URL, origin: Option[URL] = None, data: List[String] = Nil) extends AppendLogElements
@key("More") case class AppendLogMore(loc: URL, policy: Policy = Normal, data: List[String] = Nil) extends AppendLogElements
