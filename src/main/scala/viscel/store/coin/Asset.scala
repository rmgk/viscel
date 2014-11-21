package viscel.store.coin

import org.neo4j.graphdb.{Relationship, Node}
import viscel.database.{Ntx, NodeOps, rel}
import viscel.shared.{AbsUri, Story}
import viscel.store.{Metadata, StoryCoin}

import scala.annotation.tailrec

final case class Asset(self: Node) extends StoryCoin with Metadata {

	def blob(implicit neo: Ntx): Option[Blob] = self.to(rel.blob).map { Blob.apply }
	def blob_=(bn: Blob)(implicit neo: Ntx): Relationship = self.to_=(rel.blob, bn.self)

	def next(implicit neo: Ntx): Option[Asset] = self.to(rel.skip).map { Asset.apply }
	def prev(implicit neo: Ntx): Option[Asset] = self.from(rel.skip).map { Asset.apply }

	def position(implicit neo: Ntx): Int = {
		@tailrec
		def pos(oen: Option[Node], n: Int): Int = oen match {
			case None => n
			case Some(en) => pos(en.from(rel.skip), n + 1)
		}
		pos(self.from(rel.skip), 1)
	}

	def distanceToLast(implicit neo: Ntx): Int = {
		@tailrec
		def dist(oen: Option[Node], n: Int): Int = oen match {
			case None => n
			case Some(en) => dist(en.to(rel.skip), n + 1)
		}
		dist(self.to(rel.skip), 0)
	}

	def origin(implicit neo: Ntx): AbsUri = AbsUri.fromString(self[String]("origin"))
	def source(implicit neo: Ntx): AbsUri = AbsUri.fromString(self[String]("source"))

	override def story(implicit neo: Ntx): Story.Asset = Story.Asset(source, origin, metadata())

	def string(implicit neo: Ntx): String = s"Asset(${ collection.name })"

	override def toString() = s"Asset($self)"
}
