package viscel.store

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.neo4j.graphdb.Node


trait ViscelNode extends StrictLogging {
	def self: Node
	def nid: Long = self.getId
}

