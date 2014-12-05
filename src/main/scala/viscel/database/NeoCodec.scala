package viscel.database

import org.neo4j.graphdb.Node
import viscel.shared.JsonCodecs.{stringMapR, stringMapW}
import viscel.shared.Story
import viscel.shared.Story.{Asset, Blob, Chapter, Core, Failed, More}
import Implicits.NodeOps

import scala.collection.immutable.Map
import scala.Predef.ArrowAssoc

trait NeoCodec[T] {
	def read(node: Node)(implicit ntx: Ntx): T
	def write(value: T)(implicit ntx: Ntx): Node
}

object NeoCodec {

	import viscel.generated.NeoCodecs._

	def load[S](node: Node)(implicit ntx: Ntx, codec: NeoCodec[S]): S = codec.read(node)

	def create[S](desc: S)(implicit neo: Ntx, codec: NeoCodec[S]): Node = codec.write(desc)

	implicit val storyCodec: NeoCodec[Story] = new NeoCodec[Story] {
		override def write(value: Story)(implicit ntx: Ntx): Node = value match {
			case s @ More(loc, kind) => create(s)
			case s @ Chapter(name, metadata) => create(s)
			case s @ Asset(source, origin, metadata, blob) => create(s)
			case s @ Core(kind, id, name, metadata) => create(s)
			case s @ Blob(sha1, mediatype) => create(s)
			case s @ Failed(reason) => throw new IllegalArgumentException(s"can not write $s")
		}
		override def read(node: Node)(implicit ntx: Ntx): Story =
			if (node.hasLabel(label.Chapter)) load[Chapter](node)
			else if (node.hasLabel(label.Asset)) load[Asset](node)
			else if (node.hasLabel(label.More)) load[More](node)
			else if (node.hasLabel(label.Blob)) load[Blob](node)
			else if (node.hasLabel(label.Core)) load[Core](node)
			else Failed(s"$node is not a story" :: Nil)
	}

	implicit val chapterCodec: NeoCodec[Chapter] = case2RW[Chapter, String, String](label.Chapter, "name", "metadata")(
		readf = (n, md) => Chapter(n, upickle.read[Map[String, String]](md)),
		writef = chap => (chap.name, upickle.write(chap.metadata)))

	case3RW[Asset, String, String, String](label.Asset, "source", "origin", "metadata")(
		readf = (s, o, md) => Asset(s, o, upickle.read[Map[String, String]](md)),
		writef = asset => (asset.source, asset.origin, upickle.write(asset.metadata)))

	implicit val assetCodec: NeoCodec[Asset] = new NeoCodec[Asset] {
		override def write(value: Asset)(implicit ntx: Ntx): Node = {
			val asset = ntx.create(label.Asset, "source" -> value.source.toString, "origin" -> value.origin.toString, "metadata" -> upickle.write(value.metadata))
			value.blob.foreach{ b =>
				val blob = create(b)
				asset.to_=(rel.blob, blob)
			}
			asset
		}
		override def read(node: Node)(implicit ntx: Ntx): Asset = {
			val blob = Option(node.to(rel.blob)).map(load[Blob])
			Asset(node.prop[String]("source"), node.prop[String]("origin"), upickle.read[Map[String,String]](node.prop[String]("metadata")), blob)
		}
	}

	implicit val moreCodec: NeoCodec[More] = case2RW[More, String, String](label.More, "loc", "kind")(
		readf = (l, p) => More(l, p),
		writef = more => (more.loc, more.kind))

	implicit val blobCodec: NeoCodec[Story.Blob] = case2RW[Blob, String, String](label.Blob, "sha1", "mediatype")(
		readf = Blob.apply,
		writef = Blob.unapply _ andThen (_.get))

	implicit val coreCodec: NeoCodec[Core] = case4RW[Core, String, String, String, String](label.Core, "kind", "id", "name", "metadata")(
		readf = (k, i, n, md) => Core(k, i, n,  upickle.read[Map[String, String]](md)),
		writef = core => (core.kind, core.id, core.name, upickle.write(core.metadata)))


}
