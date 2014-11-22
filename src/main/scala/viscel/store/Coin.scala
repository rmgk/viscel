package viscel.store

import org.neo4j.graphdb.{Relationship, Node}
import viscel.database.Traversal.findForward
import viscel.database.{Traversal, rel, Ntx, label, NodeOps}
import viscel.database.label.SimpleLabel
import viscel.shared.{Story, AbsUri}


trait Coin extends Any {
	def self: Node
	def nid(implicit neo: Ntx): Long = self.getId
}

object Coin {
	class CheckNode[N](label: SimpleLabel, f: Node => N) extends (Node => Option[N]) {
		def unapply(node: Node): Option[N] = if (node.hasLabel(label)) Some(f(node)) else None
		override def apply(node: Node): Option[N] = unapply(node)
	}

	object isAsset extends CheckNode(label.Asset, Asset.apply)
	object isPage extends CheckNode(label.Page, Page.apply)
	object isChapter extends CheckNode(label.Chapter, Chapter.apply)
	object isCore extends CheckNode(label.Core, Core.apply)
	object isCollection extends CheckNode(label.Collection, Collection.apply)
	object isBlob extends CheckNode(label.Blob, Blob.apply)

	object hasStory extends (Node => Option[StoryCoin]) {
		def unapply(node: Node): Option[StoryCoin] = isAsset(node).orElse(isPage(node)).orElse(isChapter(node)).orElse(isCore(node))
		def apply(node: Node): Option[StoryCoin] = unapply(node)
	}


	final case class Asset(self: Node) extends AnyVal with StoryCoin with Metadata {

		def blob(implicit neo: Ntx): Option[Blob] = self.to(rel.blob).map { Blob.apply }
		def blob_=(bn: Blob)(implicit neo: Ntx): Relationship = self.to_=(rel.blob, bn.self)

		def origin(implicit neo: Ntx): AbsUri = AbsUri.fromString(self[String]("origin"))
		def source(implicit neo: Ntx): AbsUri = AbsUri.fromString(self[String]("source"))

		override def story(implicit neo: Ntx): Story.Asset = Story.Asset(source, origin, metadata())
	}

	final case class Blob(self: Node) extends AnyVal with StoryCoin with Coin {

		def sha1(implicit neo: Ntx): String = self[String]("sha1")
		def mediatype(implicit ntx: Ntx): String = self[String]("mediatype")

		override def story(implicit neo: Ntx): Story = Story.Blob(sha1, mediatype)
	}

	final case class Chapter(self: Node) extends AnyVal with StoryCoin with Metadata {

		def name(implicit neo: Ntx): String = self[String]("name")

		override def story(implicit neo: Ntx): Story.Chapter = Story.Chapter(name, metadata())

	}

	final case class Core(self: Node) extends AnyVal with StoryCoin with Metadata {

		def kind(implicit neo: Ntx): String = self[String]("kind")
		def id(implicit neo: Ntx): String = self[String]("id")
		def name(implicit neo: Ntx): String = self[String]("name")

		override def story(implicit neo: Ntx): Story.Core = Story.Core(kind, id, name, metadata())
	}

	final case class Page(self: Node) extends AnyVal with StoryCoin {
		def location(implicit neo: Ntx): AbsUri = self[String]("location")
		def pagetype(implicit neo: Ntx): String = self[String]("pagetype")

		override def story(implicit neo: Ntx): Story.More = Story.More(location, pagetype)
	}



}
