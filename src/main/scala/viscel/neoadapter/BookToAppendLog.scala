package viscel.neoadapter

import java.time.Instant

import org.neo4j.graphdb.Node
import viscel.neoadapter.Implicits.NodeOps
import viscel.scribe.{ScribeBlob, ScribeEntry, ScribePage, Vurl, WebContent, Article => AppendLogArticle, Chapter => AppendLogChapter, Link => AppendLogMore}
import viscel.shared.Blob

import scala.annotation.tailrec
import scala.collection.immutable.Map

object BookToAppendLog {

	def listToMap[T](data: List[T]): Map[T, T] = data.sliding(2, 2).map(l => (l(0), l(1))).toMap
	def mapToList[T](map: Map[T, T]): List[T] = map.flatMap { case (a, b) => List(a, b) }.toList




	def bookToEntries(book: Book)(implicit ntx: Ntx): List[ScribeEntry] = {
		def loadEntries(node: Node): WebContent = {
			Codec.load[NeoStory](node) match {
				case More(loc, policy, data) => AppendLogMore(loc, policy, data)
				case a@Asset(blob, origin, 0, data) => {
					val blobUrl = blob.getOrElse {
						val blob = Codec.load[Blob](node.to(rel.blob))
						Vurl.blobPlaceholder(blob)
					}
					val originUrl = origin.getOrElse(blobUrl)
					AppendLogArticle(blobUrl, originUrl, listToMap(data))
				}
				case Asset(blob, origin, 1, data) => AppendLogChapter(data.head)
				case a@Asset(_, _, _, _) => throw new IllegalArgumentException(s"unknown asset kind: $a")
			}
		}
		@tailrec
		def go(remaining: List[Node], acc: List[ScribeEntry]): List[ScribeEntry] = remaining match {
			case Nil => acc
			case h :: t =>
				if (h.hasRelationship(rel.blob)) {
					val asset = Codec.load[Asset](h)
					val blob = Codec.load[Blob](h.to(rel.blob))
					val blobUrl = asset.blob.getOrElse {
						Vurl.blobPlaceholder(blob)
					}
					val entry = ScribeBlob(ref = blobUrl, loc = blobUrl, blob = blob, date = Instant.now())
					go(t, entry :: acc)
				}
				else if (h.layer.isEmpty) {
					go(t, acc)
				}
				else {
					val nodes = h.layer.nodes
					val location = if (h.hasLabel(label.More)) Codec.load[More](h).loc else Vurl.entrypoint
					val stories: List[WebContent] = nodes.map {loadEntries}
					val entry = ScribePage(contents = stories, ref = location, loc = location, date = Instant.now())
					go(nodes ::: t, entry :: acc)
				}
		}
		go(List(book.self), Nil).reverse
	}
}
