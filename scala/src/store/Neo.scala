package viscel.store

import com.typesafe.scalalogging.slf4j.Logging
import org.neo4j.cypher.ExecutionEngine
import org.neo4j.graphdb.DynamicLabel
import org.neo4j.graphdb.DynamicRelationshipType
import org.neo4j.graphdb.factory.GraphDatabaseFactory
import org.neo4j.graphdb.GraphDatabaseService
import org.neo4j.graphdb.Label
import org.neo4j.graphdb.Node
import scala.collection.JavaConversions._

object Neo {
	val db = new GraphDatabaseFactory().newEmbeddedDatabase("neoViscelStore")
	val ee = new ExecutionEngine(db)

	def execute(query: String, args: Tuple2[String, Any]*) = ee.execute(query.stripMargin.trim, args.toMap[String, Any])

	def apply(q: String) = execute(q).dumpToString

	def shutdown() = db.shutdown()

	def node(label: Label, property: String, value: Any) = tx { db =>
		db.findNodesByLabelAndProperty(label, property, value).toStream match {
			case Stream(node) => Some(node)
			case Stream() => None
			case Stream(_, _) => throw new java.lang.IllegalStateException(s"found more than one entry for ($label) ${property}: $value")
		}
	}

	def create(label: Label, attributes: (String, Any)*): Node = Neo.tx { db =>
		val node = db.createNode(label)
		attributes.foreach { case (k, v) => node.setProperty(k, v) }
		node
	}

	def tx[R](f: GraphDatabaseService => R): R = {
		val tx = db.beginTx
		try {
			val res = f(db)
			tx.success
			res
		}
		finally {
			tx.finish
		}
	}

	def txs[R](f: => R): R = tx(_ => f)

}
