package viscel.store

import org.neo4j.graphdb.{Label, Node}

import scala.language.implicitConversions
import scala.collection.JavaConversions._

class CoreNode(val self: Node) extends ViscelNode {
	override def selfLabel: Label = label.Core

	def kind: String = Neo.txs { self[String]("kind") }
	def id: String = Neo.txs { self[String]("id") }
	def name: String = Neo.txs { self[String]("name") }

	def apply[R](k: String) = Neo.txs { self[R](k) }
	def get[R](k: String) = Neo.txs { self.get[R](k) }
}

object CoreNode {
	def apply(node: Node) = new CoreNode(node)
	def apply(nodeId: Long) = new CoreNode(Neo.tx { _.getNodeById(nodeId) })

	def create(kind: String, id: String, name: String, attributes: Map[String, Any]) = CoreNode(
		Neo.create(label.Core,  attributes + ("id" -> id) + ("kind" -> kind) + ("name" -> name)))

	def updateOrCreate(kind: String, id: String, name: String, attributes: Map[String, Any]) = Neo.txs {
		Neo.node(label.Core, "id", id).fold{create(kind, id, name, attributes)}{ node: Node =>
			node.getPropertyKeys.foreach(node.removeProperty)
			(attributes + ("name" -> name) + ("id" -> id) + ("kind" -> kind)).foreach{ case (k, v) => node.setProperty(k, v) }
			CoreNode(node)
		}
	}
}
