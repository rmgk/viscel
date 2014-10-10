package viscel.store.coin

import org.neo4j.graphdb.Node
import viscel.crawler.AbsUri
import viscel.narration.Story
import viscel.store.archive.{NodeOps, rel}
import viscel.store.{Metadata, StoryCoin}

import scala.annotation.tailrec

final case class Asset(self: Node) extends StoryCoin with Metadata {

	def blob: Option[Blob] = self.to(rel.blob).map { Blob.apply }
	def blob_=(bn: Blob) = self.to_=(rel.blob, bn.self)

	def nextAsset: Option[Asset] = self.to(rel.skip).map { Asset.apply }
	def prevAsset: Option[Asset] = self.from(rel.skip).map { Asset.apply }

	def position: Int = {
		@tailrec
		def pos(oen: Option[Node], n: Int): Int = oen match {
			case None => n
			case Some(en) => pos(en.from(rel.skip), n + 1)
		}
		pos(self.from(rel.skip), 1)
	}

	def distanceToLast: Int = {
		@tailrec
		def dist(oen: Option[Node], n: Int): Int = oen match {
			case None => n
			case Some(en) => dist(en.to(rel.skip), n + 1)
		}
		dist(self.to(rel.skip), 0)
	}

	def origin: AbsUri = AbsUri.fromString(self[String]("origin"))
	def source: AbsUri = AbsUri.fromString(self[String]("source"))

	override def story: Story.Asset = Story.Asset(source, origin, metadata())

	override def toString = s"Asset(${ collection.name })"
}
