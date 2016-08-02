package viscel.neoadapter

import java.net.URL

import akka.http.scaladsl.model.Uri
import org.neo4j.graphdb.Node
import viscel.neoadapter.Implicits.NodeOps
import viscel.scribe.{Normal, Volatile, Vurl}
import viscel.shared.Blob

import scala.Predef.ArrowAssoc
import scala.Predef.genericWrapArray
import scala.language.implicitConversions


trait Codec[T] {
	def read(node: Node)(implicit ntx: Ntx): T
	def write(value: T)(implicit ntx: Ntx): Node
}

object Codec {


	def load[S](node: Node)(implicit ntx: Ntx, codec: Codec[S]): S = codec.read(node)

	def create[S](desc: S)(implicit neo: Ntx, codec: Codec[S]): Node = codec.write(desc)

	implicit val storyCodec: Codec[NeoStory] = new Codec[NeoStory] {
		override def write(value: NeoStory)(implicit ntx: Ntx): Node = value match {
			case s@More(_, _, _) => create(s)
			case s@Asset(_, _, _, _) => create(s)
		}
		override def read(node: Node)(implicit ntx: Ntx): NeoStory =
			if (node.hasLabel(label.Asset)) load[Asset](node)
			else if (node.hasLabel(label.More)) load[More](node)
			else throw new IllegalArgumentException(s"unknown node type $node")
	}


	implicit val assetCodec: Codec[Asset] = new Codec[Asset] {
		override def write(value: Asset)(implicit ntx: Ntx): Node =
			ntx.create(label.Asset, List(
				value.blob.map(b => "blob" -> b.toString()),
				value.origin.map(o => "origin" -> o.toString()),
				Some("kind" -> value.kind),
				if (value.data.isEmpty) None else Some("data" -> value.data.toArray)
			).flatten.toMap)

		override def read(node: Node)(implicit ntx: Ntx): Asset = Asset(
			blob = node.get[String]("blob").map(Vurl.fromString),
			origin = node.get[String]("origin").map(Vurl.fromString),
			kind = node.prop[Byte]("kind"),
			data = node.get[Array[String]]("data").fold(List[String]())(a => a.toList)
		)
	}

	implicit val moreCodec: Codec[More] = new Codec[More] {
		override def write(value: More)(implicit ntx: Ntx): Node =
			ntx.create(label.More, List(
				Some("loc" -> value.loc.toString()),
				value.policy match {
					case Normal => None
					case Volatile => Some("policy" -> 0)
				},
				if (value.data.isEmpty) None else Some("data" -> value.data.toArray)
			).flatten.toMap)

		override def read(node: Node)(implicit ntx: Ntx): More = More(
			loc = Vurl.fromString(node.prop[String]("loc")),
			policy = GetPolicy.int(node.get[Byte]("policy")),
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
