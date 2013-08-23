package viscel.store

import com.typesafe.scalalogging.slf4j.Logging
import org.neo4j.cypher.ExecutionEngine
import org.neo4j.graphdb.Direction
import org.neo4j.graphdb.DynamicLabel
import org.neo4j.graphdb.DynamicRelationshipType
import org.neo4j.graphdb.Node
import org.neo4j.tooling.GlobalGraphOperations
import scala.collection.JavaConversions._
import util.Try
import viscel.Element
import viscel.time

object Collections {
	def list = Neo.tx { db => GlobalGraphOperations.at(db).getAllNodesWithLabel(labelCollection).toStream }
}
