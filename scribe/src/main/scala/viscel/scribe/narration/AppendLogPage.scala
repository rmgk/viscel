package viscel.scribe.narration

import java.net.URL
import java.time.Instant

import derive.key
import viscel.shared.Blob

sealed trait AppendLogEntry
@key("Page") case class AppendLogPage(
	/** reference that spawned this entry */
	ref: URL,
	/** location that was finally resolved and downloaded */
	loc: URL,
	contents: List[PageContent],
	date: Instant = Instant.now()
) extends AppendLogEntry
@key("Blob") case class AppendLogBlob(
	/** reference that spawned this entry */
	ref: URL,
	/** location that was finally resolved and downloaded */
	loc: URL,
	blob: Blob,
	date: Instant = Instant.now()
) extends AppendLogEntry


sealed trait Story
case class Page(article: Article, blob: AppendLogBlob) extends Story

sealed trait PageContent
@key("Chapter") case class Chapter(name: String) extends PageContent with Story
@key("Article") case class Article(ref: URL, origin: URL, data: Map[String, String] = Map()) extends PageContent
@key("Link") case class Link(ref: URL, policy: Policy = Normal, data: List[String] = Nil) extends PageContent


sealed trait Policy
@key("Normal") case object Normal extends Policy
@key("Volatile") case object Volatile extends Policy


