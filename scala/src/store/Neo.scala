package viscel.store

import com.typesafe.scalalogging.slf4j.Logging
import org.neo4j.cypher.ExecutionEngine
import org.neo4j.graphdb.DynamicLabel
import org.neo4j.graphdb.DynamicRelationshipType
import org.neo4j.graphdb.factory.GraphDatabaseFactory
import org.neo4j.graphdb.GraphDatabaseService
import org.neo4j.graphdb.Node

object Neo {
	val db = new GraphDatabaseFactory().newEmbeddedDatabase( "neoViscelStore" )
	val ee = new ExecutionEngine( db )

	def execute(query: String, args: Tuple2[String, Any]*) = ee.execute(query.stripMargin.trim, args.toMap[String, Any])

	def apply(q: String) = execute(q).dumpToString

	def shutdown() = db.shutdown()

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

}
