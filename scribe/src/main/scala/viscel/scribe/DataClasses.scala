package viscel.scribe

import java.time.Instant

import derive.key
import viscel.shared.Blob

sealed trait AppendLogEntry
@key("Page") case class AppendLogPage(
	/** reference that spawned this entry */
	ref: Vuri,
	/** location that was finally resolved and downloaded */
	loc: Vuri,
	contents: List[WebContent],
	date: Instant = Instant.now()
) extends AppendLogEntry
@key("Blob") case class AppendLogBlob(
	/** reference that spawned this entry */
	ref: Vuri,
	/** location that was finally resolved and downloaded */
	loc: Vuri,
	blob: Blob,
	date: Instant = Instant.now()
) extends AppendLogEntry


sealed trait Entry
case class ArticleBlob(article: Article, blob: AppendLogBlob) extends Entry

sealed trait WebContent
@key("Chapter") case class Chapter(name: String) extends WebContent with Entry
@key("Article") case class Article(ref: Vuri, origin: Vuri, data: Map[String, String] = Map()) extends WebContent
@key("Link") case class Link(ref: Vuri, policy: Policy = Normal, data: List[String] = Nil) extends WebContent


sealed trait Policy
@key("Normal") case object Normal extends Policy
@key("Volatile") case object Volatile extends Policy


