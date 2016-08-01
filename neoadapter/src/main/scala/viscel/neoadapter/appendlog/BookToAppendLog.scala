package viscel.neoadapter.appendlog

import java.net.URL

import org.neo4j.graphdb.Node
import viscel.neoadapter.database.Implicits.NodeOps
import viscel.neoadapter.database.{Book, Codec, Ntx, label, rel}
import viscel.neoadapter.narration.{Asset, Blob, More, Story}

import scala.annotation.tailrec
import scala.collection.immutable.Map

object BookToAppendLog {

	def listToMap[T](data: List[T]): Map[T, T] = data.sliding(2, 2).map(l => (l(0), l(1))).toMap
	def mapToList[T](map: Map[T, T]): List[T] = map.flatMap { case (a, b) => List(a, b) }.toList


	def bookToEntries(book: Book)(implicit ntx: Ntx): List[AppendLogEntry] = {
		def loadEntries(node: Node): AppendLogElements = {
			Codec.load[Story](node) match {
				case More(loc, policy, data) => AppendLogMore(loc, policy, data)
				case a@Asset(blob, origin, 0, data) => {
					val blobUrl = blob.getOrElse {
						val blob = Codec.load[Blob](node.to(rel.blob))
						new URL(s"http://${blob.sha1}.sha1")
					}
					val originUrl = origin.getOrElse(blobUrl)
					AppendLogArticle(blobUrl, originUrl, listToMap(data))
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
					val entry = AppendLogBlob(ref = blobUrl, loc = blobUrl, sha1 = blob.sha1, mime = blob.mime)
					go(t, entry :: acc)
				}
				else if (h.layer.isEmpty) {
					go(t, acc)
				}
				else {
					val nodes = h.layer.nodes
					val location = if (h.hasLabel(label.More)) Codec.load[More](h).loc else new URL("http://initial.entry")
					val stories: List[AppendLogElements] = nodes.map {loadEntries}
					val entry = AppendLogPage(contents = stories, ref = location, loc = location)
					go(nodes ::: t, entry :: acc)
				}
		}
		go(List(book.self), Nil).reverse
	}
}
