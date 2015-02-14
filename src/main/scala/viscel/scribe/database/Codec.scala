package viscel.scribe.database

import org.neo4j.graphdb.Node
import viscel.scribe.database.Implicits.NodeOps
import viscel.scribe.narration.SelectMore.stringToURL
import viscel.scribe.narration.{Asset, Blob, More, Policy, Story}

import scala.Predef.ArrowAssoc
import scala.Predef.genericArrayOps
import scala.language.implicitConversions


trait Codec[T] {
	def read(node: Node)(implicit ntx: Ntx): T
	def write(value: T)(implicit ntx: Ntx): Node
}

object Codec {

	def load[S](node: Node)(implicit ntx: Ntx, codec: Codec[S]): S = codec.read(node)

	def create[S](desc: S)(implicit neo: Ntx, codec: Codec[S]): Node = codec.write(desc)

	implicit val storyCodec: Codec[Story] = new Codec[Story] {
		override def write(value: Story)(implicit ntx: Ntx): Node = value match {
			case s@More(_, _, _) => create(s)
			case s@Asset(_, _, _, _) => create(s)
		}
		override def read(node: Node)(implicit ntx: Ntx): Story =
			if (node.hasLabel(label.Asset)) load[Asset](node)
			else if (node.hasLabel(label.More)) load[More](node)
			else throw new IllegalArgumentException(s"unknown node type ${ node }")
	}


	implicit val assetCodec: Codec[Asset] = new Codec[Asset] {
		override def write(value: Asset)(implicit ntx: Ntx): Node =
			ntx.create(label.Asset, List(
				value.blob.map(b => "blob" -> b.toExternalForm),
				value.origin.map(o => "origin" -> o.toExternalForm),
				Some("kind" -> value.kind),
				if (value.data.length == 0) None else Some("data" -> value.data.toArray)
			).flatten.toMap)

		override def read(node: Node)(implicit ntx: Ntx): Asset = Asset(
			blob = node.get[String]("blob").map(stringToURL),
			origin = node.get[String]("origin").map(stringToURL),
			kind = node.prop[Byte]("kind"),
			data = node.get[Array[String]]("data").fold(List[String]())(a => a.toList)
		)
	}

	implicit val moreCodec: Codec[More] = new Codec[More] {
		override def write(value: More)(implicit ntx: Ntx): Node =
			ntx.create(label.More, List(
				Some("loc" -> value.loc.toExternalForm),
				value.policy.ext.map("policy" -> _),
				if (value.data.length == 0) None else Some("data" -> value.data.toArray)
			).flatten.toMap)

		override def read(node: Node)(implicit ntx: Ntx): More = More(
			loc = node.prop[String]("loc"),
			policy = Policy.int(node.get[Byte]("policy")),
			data = node.get[Array[String]]("data").fold(List[String]())(a => a.toList)
		)
	}

	implicit val blobCodec: Codec[Blob] = new Codec[Blob] {
		override def write(value: Blob)(implicit ntx: Ntx): Node =
			ntx.create(label.Blob, "sha1" -> value.sha1, "mime" -> value.mime)

		override def read(node: Node)(implicit ntx: Ntx): Blob =
			Blob(sha1 = node.prop[String]("sha1"), mime = node.prop[String]("mime"))
	}

}
