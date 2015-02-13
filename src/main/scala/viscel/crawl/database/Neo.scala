package viscel.crawl.database

import org.neo4j.graphdb.factory.{GraphDatabaseFactory, GraphDatabaseSettings}
import org.neo4j.graphdb.{GraphDatabaseService, Label, Node}
import org.neo4j.helpers.Settings
import org.neo4j.tooling.GlobalGraphOperations
import viscel.crawl.Log

import scala.collection.JavaConverters._
import scala.collection.Map


trait Neo {
	def tx[R](f: Ntx => R): R
	def txs[R](f: => R): R
}

trait Ntx {
	def db: GraphDatabaseService

	def node(label: Label, property: String, value: Any): Option[Node]
	def nodes(label: Label): List[Node]

	def create(label: Label, attributes: (String, Any)*): Node
	def create(label: Label, attributes: Map[String, Any]): Node

	def delete(node: Node): Unit
}


class NeoInstance(path: String) extends Neo with Ntx {
	val db: GraphDatabaseService = new GraphDatabaseFactory().newEmbeddedDatabaseBuilder(path)
		.setConfig(GraphDatabaseSettings.keep_logical_logs, Settings.FALSE).newGraphDatabase()

	sys.addShutdownHook { shutdown() }


	def shutdown(): Unit = {
		//txt("export") { ArchiveExport.exportAll(_) }
		db.shutdown()
	}

	def node(label: Label, property: String, value: Any): Option[Node] = {
		def go() =
			db.findNodesByLabelAndProperty(label, property, value).asScala.toList match {
				case List(node) => Some(node)
				case Nil => None
				case _ => throw new java.lang.IllegalStateException(s"found more than one entry for $label($property=$value)")
			}
		go()
	}

	def nodes(label: Label): List[Node] = txs { GlobalGraphOperations.at(db).getAllNodesWithLabel(label).asScala.toList }

	def create(label: Label, attributes: (String, Any)*): Node = create(label, attributes.toMap)
	def create(label: Label, attributes: Map[String, Any]): Node = {
		Log.debug(s"create node $label($attributes)")
		val node = db.createNode(label)
		node.setProperty("created", System.currentTimeMillis)
		attributes.foreach { case (k, v) => node.setProperty(k, v) }
		node
	}

	def delete(node: Node) = {
		Log.trace(s"delete node $node")
		node.getRelationships.asScala.foreach { _.delete() }
		node.delete()
	}

	def tx[R](f: Ntx => R): R = {
		val tx = db.beginTx() //db.tx.unforced.begin()
		try {
			val res = f(this)
			tx.success()
			res
		}
		finally {
			tx.close()
		}
	}

	def txs[R](f: => R): R = tx(_ => f)

}
