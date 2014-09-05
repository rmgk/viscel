package viscel.store

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.neo4j.cypher.ExecutionEngine
import org.neo4j.graphdb.factory.{GraphDatabaseFactory, GraphDatabaseSettings}
import org.neo4j.graphdb.{GraphDatabaseService, Label, Node}
import org.neo4j.helpers.Settings
import org.neo4j.tooling.GlobalGraphOperations
import viscel.time

import scala.collection.JavaConverters._
import scala.collection.Map

object Neo extends StrictLogging {
	val db = new GraphDatabaseFactory().newEmbeddedDatabaseBuilder("neoViscelStore")
		.setConfig(GraphDatabaseSettings.keep_logical_logs, Settings.FALSE).newGraphDatabase()
	val ee = new ExecutionEngine(db)

	def execute(query: String, args: (String, Any)*) = ee.execute(Predef.wrapString(query).stripMargin.trim, args.toMap[String, Any])

	def apply(q: String) = execute(q).dumpToString()

	def shutdown(): Unit = db.shutdown()

	def node(label: Label, property: String, value: Any, logTime: Boolean = true): Option[Node] = {
		def go() = tx { db =>
			db.findNodesByLabelAndProperty(label, property, value).asScala.toList match {
				case List(node) => Some(node)
				case Nil => None
				case _ => throw new java.lang.IllegalStateException(s"found more than one entry for $label($property=$value)")
			}
		}
		if (logTime) time(s"query $label($property=$value)") { go() }
		else go()
	}

	def nodes(label: Label) = txs { GlobalGraphOperations.at(db).getAllNodesWithLabel(label).asScala.toList }

	def create(label: Label, attributes: (String, Any)*): Node = create(label, attributes.toMap)
	def create(label: Label, attributes: Map[String, Any]): Node = Neo.tx { db =>
		logger.debug(s"create node $label($attributes)")
		val node = db.createNode(label)
		node.setProperty("created", System.currentTimeMillis)
		attributes.foreach { case (k, v) => node.setProperty(k, v) }
		node
	}

	def delete(node: Node) = txs {
		node.getRelationships.asScala.foreach { _.delete() }
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
