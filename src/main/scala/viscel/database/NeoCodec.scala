package viscel.database

import org.neo4j.graphdb.Node
import viscel.shared.JsonCodecs.stringMapR
import viscel.shared.Story
import viscel.shared.Story.{Asset, Blob, Chapter, Core, Failed, More}

import scala.collection.immutable.Map

trait NeoCodec[T] {
	def read(node: Node)(implicit ntx: Ntx): T
	def write(value: T)(implicit ntx: Ntx): Node
}

object NeoCodec {

	import viscel.generated.NeoCodecs._

	def story[S](node: Node)(implicit ntx: Ntx, codec: NeoCodec[S]): S = codec.read(node)

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
			if (node.hasLabel(label.Chapter)) story[Chapter](node)
			else if (node.hasLabel(label.Asset)) story[Asset](node)
			else if (node.hasLabel(label.More)) story[More](node)
			else if (node.hasLabel(label.Blob)) story[Blob](node)
			else if (node.hasLabel(label.Core)) story[Core](node)
			else Failed(s"$node is not a story" :: Nil)
	}

	implicit val chapterCodec: NeoCodec[Chapter] = case2RW[Chapter, String, String](label.Chapter, "name", "metadata")(
		readf = (n, md) => Chapter(n, upickle.read[Map[String, String]](md)),
		writef = chap => (chap.name, upickle.write(chap.metadata)))

	implicit val assetCodec: NeoCodec[Asset] = case3RW[Asset, String, String, String](label.Asset, "source", "origin", "metadata")(
		readf = (s, o, md) => Asset(s, o, upickle.read[Map[String, String]](md)),
		writef = asset => (asset.source, asset.origin, upickle.write(asset.metadata)))

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
