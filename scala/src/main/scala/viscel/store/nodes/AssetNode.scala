package viscel.store.nodes

import org.neo4j.graphdb.Node
import viscel.crawler.AbsUri
import viscel.description.Asset
import viscel.store._

import scala.annotation.tailrec

final case class AssetNode(self: Node) extends ArchiveNode with Metadata {

	def blob: Option[BlobNode] = self.to(rel.blob).map { BlobNode.apply }
	def blob_=(bn: BlobNode) = self.to_=(rel.blob, bn.self)

	def nextAsset: Option[AssetNode] = self.to(rel.skip).map { AssetNode.apply }
	def prevAsset: Option[AssetNode] = self.from(rel.skip).map { AssetNode.apply }

	def position: Int = {
		@tailrec
		def pos(oen: Option[AssetNode], n: Int): Int = oen match {
			case None => n
			case Some(en) => pos(en.prevAsset, n + 1)
		}
		pos(prevAsset, 1)
	}

	def distanceToLast: Int = {
		@tailrec
		def dist(oen: Option[AssetNode], n: Int): Int = oen match {
			case None => n
			case Some(en) => dist(en.nextAsset, n + 1)
		}
		dist(nextAsset, 0)
	}

	def origin: AbsUri = AbsUri.fromString(self[String]("origin"))
	def source: AbsUri = AbsUri.fromString(self[String]("source"))

	override def description: Asset = Asset(source, origin, metadata())

	override def toString = s"Asset(${ collection.name })"
}
