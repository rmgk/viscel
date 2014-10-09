package viscel.store.coin

import org.neo4j.graphdb.Node
import viscel.crawler.AbsUri
import viscel.description.Story
import viscel.store.{rel, Metadata, ArchiveNode, NodeOps}

import scala.annotation.tailrec

final case class Asset(self: Node) extends ArchiveNode with Metadata {

	def blob: Option[Blob] = self.to(rel.blob).map { Blob.apply }
	def blob_=(bn: Blob) = self.to_=(rel.blob, bn.self)

	def nextAsset: Option[Asset] = self.to(rel.skip).map { Asset.apply }
	def prevAsset: Option[Asset] = self.from(rel.skip).map { Asset.apply }

	def position: Int = {
		@tailrec
		def pos(oen: Option[Node], n: Int): Int = oen match {
			case None => n
			case Some(en) => pos(en.to(rel.skip), n + 1)
		}
		pos(self.to(rel.skip), 1)
	}

	def distanceToLast: Int = {
		@tailrec
		def dist(oen: Option[Node], n: Int): Int = oen match {
			case None => n
			case Some(en) => dist(en.from(rel.skip), n + 1)
		}
		dist(self.from(rel.skip), 0)
	}

	def origin: AbsUri = AbsUri.fromString(self[String]("origin"))
	def source: AbsUri = AbsUri.fromString(self[String]("source"))

	override def story: Story.Asset = Story.Asset(source, origin, metadata())

	override def toString = s"Asset(${ collection.name })"
}
