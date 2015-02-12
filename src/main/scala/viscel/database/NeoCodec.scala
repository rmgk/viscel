package viscel.database

import org.neo4j.graphdb.Node
import viscel.database.Implicits.NodeOps
import viscel.shared.Story.{Asset, Blob, More}
import viscel.shared.{Story}
import viscel.narration.SelectUtil.{stringToURL}

import scala.Predef.ArrowAssoc
import scala.collection.immutable.Map
import scala.Predef.genericArrayOps
import scala.language.implicitConversions
import viscel.generated.NeoCodecs._


trait NeoCodec[T] {
	def read(node: Node)(implicit ntx: Ntx): T
	def write(value: T)(implicit ntx: Ntx): Node
}

object NeoCodec {


	def load[S](node: Node)(implicit ntx: Ntx, codec: NeoCodec[S]): S = codec.read(node)

	def create[S](desc: S)(implicit neo: Ntx, codec: NeoCodec[S]): Node = codec.write(desc)

	implicit val storyCodec: NeoCodec[Story] = new NeoCodec[Story] {
		override def write(value: Story)(implicit ntx: Ntx): Node = value match {
			case s@More(loc, kind) => create(s)
			case s@Asset(source, origin, metadata, blob) => create(s)
			case s@Blob(sha1, mediatype) => create(s)
		}
		override def read(node: Node)(implicit ntx: Ntx): Story =
			if (node.hasLabel(label.Asset)) load[Asset](node)
			else if (node.hasLabel(label.More)) load[More](node)
			else if (node.hasLabel(label.Blob)) load[Blob](node)
			else throw new IllegalArgumentException(s"unknown node type ${node}")
	}

	def serializeMetadata(m: Map[String, String]): Array[String] = m.map(e => Array(e._1, e._2)).flatten.toArray
	def deserializeMetadata(a: Array[String]): Map[String, String] = a.sliding(2,2).map(a => (a(0), a(1))).toMap


	implicit val assetCodec: NeoCodec[Asset] = new NeoCodec[Asset] {
		override def write(value: Asset)(implicit ntx: Ntx): Node = {
			val asset = ntx.create(label.Asset, "source" -> value.source.toString, "origin" -> value.origin.toString, "metadata" -> serializeMetadata(value.metadata))
			value.blob.foreach { b =>
				val blob = create(b)
				asset.to_=(rel.blob, blob)
			}
			asset
		}
		override def read(node: Node)(implicit ntx: Ntx): Asset = {
			val blob = Option(node.to(rel.blob)).map(load[Blob])
			Asset(node.prop[String]("source"), node.prop[String]("origin"), deserializeMetadata(node.prop[Array[String]]("metadata")), blob)
		}
	}

	implicit val moreCodec: NeoCodec[More] = case2RW[More, String, String](label.More, "loc", "kind")(
		readf = (l, p) => More(l, More.Kind(p)),
		writef = more => (more.loc.toExternalForm, more.kind.name))

	implicit val blobCodec: NeoCodec[Story.Blob] = case2RW[Blob, String, String](label.Blob, "sha1", "mediatype")(
		readf = Blob.apply,
		writef = Blob.unapply _ andThen (_.get))

}
