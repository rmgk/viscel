package viscel.db2to3.appendlog

import java.net.URL
import java.time.Instant

import derive.key
import viscel.db2to3.narration.{Normal, Policy}

sealed trait AppendLogEntry
@key("Page") case class AppendLogPage(
	/** reference that spawned this entry */
	ref: URL,
	/** location that was finally resolved and downloaded */
	loc: URL,
	contents: List[AppendLogElements],
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



sealed trait AppendLogElements
@key("Chapter") case class AppendLogChapter(name: String) extends AppendLogElements
@key("Article") case class AppendLogArticle(blob: URL, origin: Option[URL] = None, data: List[String] = Nil) extends AppendLogElements
@key("More") case class AppendLogMore(loc: URL, policy: Policy = Normal, data: List[String] = Nil) extends AppendLogElements
