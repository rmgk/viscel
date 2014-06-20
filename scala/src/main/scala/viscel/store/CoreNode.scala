package viscel.store

import org.neo4j.graphdb.{Label, Node}
import viscel.description._

import scala.language.implicitConversions
import scala.collection.JavaConverters._

class CoreNode(val self: Node) extends ArchiveNode with Metadata {
	override def selfLabel: Label = label.Core

	def kind: String = Neo.txs { self[String]("kind") }
	def id: String = Neo.txs { self[String]("id") }
	def name: String = Neo.txs { self[String]("name") }

	def apply[R](k: String) = Neo.txs { self[R](k) }
	def get[R](k: String) = Neo.txs { self.get[R](k) }

	override def description: Description = Neo.txs {
		CoreDescription(kind, id, name, metadata)
	}
}

object CoreNode {
	def apply(node: Node) = new CoreNode(node)
	def apply(nodeId: Long) = new CoreNode(Neo.tx { _.getNodeById(nodeId) })

	def create(desc: CoreDescription) = CoreNode(
		Neo.create(label.Core,  Metadata.prefix(desc.props) + ("id" -> desc.id) + ("kind" -> desc.kind) + ("name" -> desc.name)))

	def updateOrCreate(desc: CoreDescription) = Neo.txs {
		Neo.node(label.Core, "id", desc.id).fold{create(desc)}{ node: Node =>
			node.getPropertyKeys.asScala.foreach(node.removeProperty)
			(Metadata.prefix(desc.props) + ("name" -> desc.name) + ("id" -> desc.id) + ("kind" -> desc.kind)).foreach{ case (k, v) => node.setProperty(k, v) }
			CoreNode(node)
		}
	}
}
