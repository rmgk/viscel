package viscel.store

import org.neo4j.graphdb.{Node, Relationship}
import viscel.database.Implicits.NodeOps
import viscel.database.label.SimpleLabel
import viscel.database.{Ntx, label, rel}
import viscel.shared.{AbsUri, Story}

import scala.Predef.ArrowAssoc
import scala.collection.immutable.Map
import viscel.shared.JsonCodecs.{stringMapR, stringMapW}


sealed trait Coin extends Any {
	def self: Node
	def nid(implicit neo: Ntx): Long = self.getId
	def story(implicit neo: Ntx): Story
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
	object isBlob extends CheckNode(label.Blob, Blob.apply)

	def apply(node: Node): Coin = node match {
			case isAsset(n) => n
			case isPage(n) => n
			case isChapter(n) => n
			case isCore(n) => n
			case isBlob(n) => n
		}

	final case class Asset(self: Node) extends AnyVal with Metadata with Coin{

		def blob(implicit neo: Ntx): Option[Blob] = Option(self to rel.blob) map Blob.apply
		def blob_=(bn: Blob)(implicit neo: Ntx): Relationship = self.to_=(rel.blob, bn.self)

		def origin(implicit neo: Ntx): AbsUri = AbsUri.fromString(self.prop[String]("origin"))
		def source(implicit neo: Ntx): AbsUri = AbsUri.fromString(self.prop[String]("source"))

		override def story(implicit neo: Ntx): Story.Asset = Story.Asset(source, origin, metadata(), blob.map(_.story))
	}

	final case class Blob(self: Node) extends AnyVal with Coin {

		def sha1(implicit neo: Ntx): String = self.prop[String]("sha1")
		def mediatype(implicit ntx: Ntx): String = self.prop[String]("mediatype")

		override def story(implicit neo: Ntx): Story.Blob = Story.Blob(sha1, mediatype)
	}

	final case class Chapter(self: Node) extends AnyVal with Metadata with Coin {

		def name(implicit neo: Ntx): String = self.prop[String]("name")

		override def story(implicit neo: Ntx): Story.Chapter = Story.Chapter(name, metadata())
	}

	final case class Core(self: Node) extends AnyVal with Metadata with Coin {

		def kind(implicit neo: Ntx): String = self.prop[String]("kind")
		def id(implicit neo: Ntx): String = self.prop[String]("id")
		def name(implicit neo: Ntx): String = self.prop[String]("name")

		override def story(implicit neo: Ntx): Story.Core = Story.Core(kind, id, name, metadata())
	}

	final case class Page(self: Node) extends AnyVal with Coin {
		def location(implicit neo: Ntx): AbsUri = self.prop[String]("location")
		def pagetype(implicit neo: Ntx): String = self.prop[String]("pagetype")

		override def story(implicit neo: Ntx): Story.More = Story.More(location, pagetype)
	}


	trait Metadata extends Any {
		this: Coin =>
		def metadata()(implicit neo: Ntx): Map[String, String] = upickle.read[Map[String, String]](self.prop[String]("metadata"))
	}

	def create(desc: Story)(implicit neo: Ntx): Node = desc match {
		case Story.Chapter(name, metadata) => neo.create(label.Chapter, "metadata" -> upickle.write(metadata), "name" -> name)
		case Story.Asset(source, origin, metadata, blob) => neo.create(label.Asset, "metadata" -> upickle.write(metadata), "source" -> source.toString, "origin" -> origin.toString)
		case Story.Core(kind, id, name, metadata) => neo.create(label.Core, "metadata" -> upickle.write(metadata), "id" -> id, "kind" -> kind, "name" -> name)
		case Story.More(loc, pagetype) => neo.create(label.Page, "location" -> loc.toString, "pagetype" -> pagetype)
		case Story.Blob(sha1, mediastring) => neo.create(label.Blob, "sha1" -> sha1, "mediatype" -> mediastring)
		case Story.Failed(reason) => throw new IllegalArgumentException(reason.toString())
	}



}
