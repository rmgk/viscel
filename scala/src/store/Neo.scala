package viscel.store

import com.typesafe.scalalogging.slf4j.Logging
import org.neo4j.cypher.ExecutionEngine
import org.neo4j.graphdb.DynamicLabel
import org.neo4j.graphdb.DynamicRelationshipType
import org.neo4j.graphdb.factory.GraphDatabaseFactory
import org.neo4j.graphdb.GraphDatabaseService
import org.neo4j.kernel.GraphDatabaseAPI
import org.neo4j.graphdb.Label
import org.neo4j.graphdb.Node
import org.neo4j.tooling.GlobalGraphOperations
import scala.collection.JavaConversions._
import viscel.time

object Neo extends Logging {
	val db = new GraphDatabaseFactory().newEmbeddedDatabaseBuilder("neoViscelStore")
		.setConfig(Map("keep_logical_logs" -> "false")).newGraphDatabase().asInstanceOf[GraphDatabaseAPI]
	val ee = new ExecutionEngine(db)

	def execute(query: String, args: Tuple2[String, Any]*) = ee.execute(query.stripMargin.trim, args.toMap[String, Any])

	def apply(q: String) = execute(q).dumpToString

	def shutdown() = db.shutdown()

	def node(label: Label, property: String, value: Any) = txt(s"query $label($property=$value)") { db =>
		db.findNodesByLabelAndProperty(label, property, value).toStream match {
			case Stream(node) => Some(node)
			case Stream() => None
			case Stream(_, _) => throw new java.lang.IllegalStateException(s"found more than one entry for $label($property=$value)")
		}
	}

	def nodes(label: Label) = txs { GlobalGraphOperations.at(db).getAllNodesWithLabel(label).toIndexedSeq }

	def create(label: Label, attributes: (String, Any)*): Node = Neo.tx { db =>
		logger.info(s"create node $label($attributes)")
		val node = db.createNode(label)
		node.setProperty("created", System.currentTimeMillis)
		attributes.foreach { case (k, v) => node.setProperty(k, v) }
		node
	}

	def delete(node: Node) = txs {
		node.getRelationships.foreach { _.delete() }
		node.delete()
	}

	def tx[R](f: GraphDatabaseService => R): R = {
		val tx = db.beginTx() //db.tx.unforced.begin()
		try {
			val res = f(db)
			tx.success()
			res
		}
		finally {
			tx.close()
		}
	}

	def txt[R](desc: String)(f: GraphDatabaseService => R): R = time(desc) { tx(f) }

	def txs[R](f: => R): R = tx(_ => f)

	def txts[R](desc: String)(f: => R): R = txt(desc)(_ => f)

}
