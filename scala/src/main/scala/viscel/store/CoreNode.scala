package viscel.store

import org.neo4j.graphdb.{Label, Node}
import viscel.description._

import scala.Predef.any2ArrowAssoc
import scala.collection.JavaConverters._
import scala.language.implicitConversions

class CoreNode(val self: Node) extends ArchiveNode with Metadata {
	override def selfLabel: Label = label.Core

	def kind: String = Neo.txs { self[String]("kind") }
	def id: String = Neo.txs { self[String]("id") }
	def name: String = Neo.txs { self[String]("name") }

	override def description: CoreDescription = Neo.txs {
		CoreDescription(kind, id, name, metadata())
	}
}

object CoreNode {
	def apply(node: Node) = new CoreNode(node)
	def apply(nodeId: Long) = new CoreNode(Neo.tx { _.getNodeById(nodeId) })

	def create(desc: CoreDescription) = CoreNode(
		Neo.create(label.Core,  Metadata.prefix(desc.metadata) + ("id" -> desc.id) + ("kind" -> desc.kind) + ("name" -> desc.name)))

	def updateOrCreate(desc: CoreDescription) = Neo.txs {
		Neo.node(label.Core, "id", desc.id).fold{create(desc)}{ node: Node =>
			node.getPropertyKeys.asScala.foreach(node.removeProperty)
			(Metadata.prefix(desc.metadata) + ("name" -> desc.name) + ("id" -> desc.id) + ("kind" -> desc.kind)).foreach{ case (k, v) => node.setProperty(k, v) }
			CoreNode(node)
		}
	}
}
