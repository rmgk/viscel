package viscel.scribe.database

import java.net.URL

import org.neo4j.graphdb.Node
import viscel.scribe.appendstore.{AppendLogArticle, AppendLogBlob, AppendLogChapter, AppendLogElements, AppendLogEntry, AppendLogMore, AppendLogPage}
import viscel.scribe.database.Implicits.NodeOps
import viscel.scribe.narration.{Asset, Blob, More, Page, Story}

import scala.annotation.tailrec
import scala.util.Try

final case class Book(self: Node) extends AnyVal {

	def id(implicit ntx: Ntx): String = self.prop[String]("id")
	def name(implicit ntx: Ntx): String = self.get[String]("name").getOrElse(id)
	def name_=(value: String)(implicit neo: Ntx) = self.setProperty("name", value)
	def size(kind: Byte)(implicit ntx: Ntx): Int = {
		val sopt = self.get[Array[Int]]("size")
		val res = sopt.getOrElse {
			val size = calcSize()
			self.setProperty("size", size)
			size
		}
		if (kind < res.length) res(kind) else 0
	}

	def invalidateSize()(implicit ntx: Ntx) = self.removeProperty("size")

	private def calcSize()(implicit ntx: Ntx): Array[Int] = {
		val mapped = self.layer.recursive
			.filter(_.hasLabel(label.Asset))
			.groupBy(_.prop[Byte]("kind"))
			.mapValues(_.length)
		val size = Try(mapped.keys.max).getOrElse[Byte](-1) + 1
		val res = new Array[Int](size)
		mapped.foreach { case (k, v) => res(k) = v }
		res
	}

	def pages()(implicit ntx: Ntx): List[Page] =
		self.layer.recursive.collect {
			case n if n hasLabel label.Asset =>
				val asset = Codec.load[Asset](n)
				val blob = Option(n.to(rel.blob)).map(Codec.load[Blob])
				Page(asset, blob)
		}

	def entries()(implicit ntx: Ntx): List[AppendLogEntry] = {
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
				case a @ Asset(_, _, _, _) => throw new IllegalArgumentException(s"unknown asset kind: $a" )
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
		go(List(self), Nil).reverse
	}

}
