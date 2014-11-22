package viscel.store.coin

import org.neo4j.graphdb.{Node, Relationship}
import viscel.database.{NodeOps, Ntx, rel}
import viscel.shared.{AbsUri, Story}
import viscel.store.{Metadata, StoryCoin}

final case class Asset(self: Node) extends AnyVal with StoryCoin with Metadata {

	def blob(implicit neo: Ntx): Option[Blob] = self.to(rel.blob).map { Blob.apply }
	def blob_=(bn: Blob)(implicit neo: Ntx): Relationship = self.to_=(rel.blob, bn.self)

	def origin(implicit neo: Ntx): AbsUri = AbsUri.fromString(self[String]("origin"))
	def source(implicit neo: Ntx): AbsUri = AbsUri.fromString(self[String]("source"))

	override def story(implicit neo: Ntx): Story.Asset = Story.Asset(source, origin, metadata())
}
