package viscel.store

import org.neo4j.graphdb.Node
import viscel.crawler.AbsUri
import viscel.description.Asset

import scala.Predef.any2ArrowAssoc
import scala.annotation.tailrec

class AssetNode(val self: Node) extends ArchiveNode with Metadata {
	def selfLabel = label.Asset

	def blob: Option[BlobNode] = Neo.txs { self.to(rel.blob).map { BlobNode(_) } }
	def blob_=(bn: BlobNode) = Neo.txs { self.to_=(rel.blob, bn.self) }

	//	def nextAsset: Option[AssetNode] = Neo.txs { next.flatMap{ _.findForward { case asset: AssetNode => asset } } }
	def nextAsset: Option[AssetNode] = Neo.txs { self.to(rel.skip).map { AssetNode(_) } }
	//	def prevAsset: Option[AssetNode] = Neo.txs { prev.flatMap{ _.findBackward { case asset: AssetNode => asset } } }
	def prevAsset: Option[AssetNode] = Neo.txs { self.from(rel.skip).map { AssetNode(_) } }

	def position: Int = Neo.txs {
		@tailrec
		def pos(oen: Option[AssetNode], n: Int): Int = oen match {
			case None => n
			case Some(en) => pos(en.prevAsset, n + 1)
		}
		pos(prevAsset, 1)
	}

	def distanceToLast: Int = Neo.txs {
		@tailrec
		def dist(oen: Option[AssetNode], n: Int): Int = oen match {
			case None => n
			case Some(en) => dist(en.nextAsset, n + 1)
		}
		dist(nextAsset, 0)
	}

	def origin: AbsUri = Neo.txs { AbsUri.fromString(self[String]("origin")) }
	def source: AbsUri = Neo.txs { AbsUri.fromString(self[String]("source")) }

	override def description: Asset = Neo.txs {
		Asset(source, origin, metadata())
	}

	override def toString = s"$selfLabel(${ collection.name })"
}

object AssetNode {
	def apply(node: Node) = new AssetNode(node)
	def apply(nodeId: Long) = new AssetNode(Neo.tx { _.getNodeById(nodeId) })

	def create(asset: Asset) = {
		AssetNode(Neo.create(label.Asset, Metadata.prefix(asset.metadata) +
			("source" -> asset.source.toString) + ("origin" -> asset.origin.toString)))
	}
}
