package viscel.store

import org.neo4j.graphdb.{Label, Node}
import viscel.description.{Asset, CoreDescription, Description}

import scala.language.implicitConversions
import scala.collection.JavaConverters._

class CoreNode(val self: Node) extends ArchiveNode {
	override def selfLabel: Label = label.Core

	def kind: String = Neo.txs { self[String]("kind") }
	def id: String = Neo.txs { self[String]("id") }
	def name: String = Neo.txs { self[String]("name") }

	def apply[R](k: String) = Neo.txs { self[R](k) }
	def get[R](k: String) = Neo.txs { self.get[R](k) }

	override def description: Description = Neo.txs {
		val props = self.getPropertyKeys.asScala.map(key => key -> self[String](key)).toMap
		CoreDescription(props("kind"), props("id"), props("name"), props - "kind" - "id" - "name")
	}
}

object CoreNode {
	def apply(node: Node) = new CoreNode(node)
	def apply(nodeId: Long) = new CoreNode(Neo.tx { _.getNodeById(nodeId) })

	def create(kind: String, id: String, name: String, attributes: Map[String, Any]) = CoreNode(
		Neo.create(label.Core,  attributes + ("id" -> id) + ("kind" -> kind) + ("name" -> name)))

	def updateOrCreate(kind: String, id: String, name: String, attributes: Map[String, Any]) = Neo.txs {
		Neo.node(label.Core, "id", id).fold{create(kind, id, name, attributes)}{ node: Node =>
			node.getPropertyKeys.asScala.foreach(node.removeProperty)
			(attributes + ("name" -> name) + ("id" -> id) + ("kind" -> kind)).foreach{ case (k, v) => node.setProperty(k, v) }
			CoreNode(node)
		}
	}
}
