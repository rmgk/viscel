package viscel.store

import org.neo4j.graphdb.Node
import spray.http.{MediaType, MediaTypes}
import viscel.crawler.AbsUri

import scala.Predef.any2ArrowAssoc
import scala.language.implicitConversions

class BlobNode(val self: Node) extends ViscelNode {
	def selfLabel = label.Blob

	def sha1 = Neo.txs { self[String]("sha1") }
	def mediatype: MediaType = Neo.txs {
		val mstring = self[String]("mediatype")
		val split = mstring.split("/")
		MediaTypes.getForKey(split(0) -> split(1)).get
	}
	def source = Neo.txs { self[String]("source") }

	override def toString = s"$selfLabel($sha1)"
}

object BlobNode {
	def apply(node: Node) = new BlobNode(node)
	def apply(nodeId: Long) = new BlobNode(Neo.tx { _.getNodeById(nodeId) })

	def find(source: AbsUri) = Neo.txs { Neo.node(label.Blob, "source", source.toString(), logTime = false) }.map { apply }

	def create(sha1: String, mediatype: MediaType, source: AbsUri) = BlobNode(
		Neo.create(label.Blob,
			"sha1" -> sha1,
			"mediatype" -> mediatype.value,
			"source" -> source.toString()))
}
