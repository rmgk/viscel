package viscel.scribe.appendstore

import java.net.URL

import org.neo4j.graphdb.Node
import viscel.scribe.database.Implicits.NodeOps
import viscel.scribe.database.{Book, Codec, Ntx, label, rel}
import viscel.scribe.narration.{Asset, Blob, More, Story}

import scala.annotation.tailrec

object FromNeoBook {

	def bookToEntries(book: Book)(implicit ntx: Ntx): List[AppendLogEntry] = {
		def loadEntries(node: Node): AppendLogElements = {
			Codec.load[Story](node) match {
				case More(loc, policy, data) => AppendLogMore(loc, policy, data)
				case a@Asset(blob, origin, 0, data) => {
					val blobUrl = blob.getOrElse {
						val blob = Codec.load[Blob](node.to(rel.blob))
						new URL(s"http://${blob.sha1}.sha1")
					}
					AppendLogArticle(blobUrl, origin, data)
				}
				case Asset(blob, origin, 1, data) => AppendLogChapter(data.head)
				case a@Asset(_, _, _, _) => throw new IllegalArgumentException(s"unknown asset kind: $a")
			}
		}
		@tailrec
		def go(remaining: List[Node], acc: List[AppendLogEntry]): List[AppendLogEntry] = remaining match {
			case Nil => acc
			case h :: t =>
				if (h.hasRelationship(rel.blob)) {
					val asset = Codec.load[Asset](h)
					val blob = Codec.load[Blob](h.to(rel.blob))
					val blobUrl = asset.blob.getOrElse {
						new URL(s"http://${blob.sha1}.sha1")
					}
					val entry = AppendLogBlob(initialLocation = blobUrl, resolvedLocation = blobUrl, sha1 = blob.sha1, mime = blob.mime)
					go(t, entry :: acc)
				}
				else if (h.layer.isEmpty) {
					go(t, acc)
				}
				else {
					val nodes = h.layer.nodes
					val location = if (h.hasLabel(label.More)) Codec.load[More](h).loc else new URL("http://initial.entry")
					val stories: List[AppendLogElements] = nodes.map {loadEntries}
					val entry = AppendLogPage(contents = stories, initialLocation = location, resolvedLocation = location)
					go(nodes ::: t, entry :: acc)
				}
		}
		go(List(book.self), Nil).reverse
	}
}
