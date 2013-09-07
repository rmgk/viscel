package viscel.store

import com.typesafe.scalalogging.slf4j.Logging
import org.neo4j.cypher.ExecutionEngine
import org.neo4j.graphdb.DynamicLabel
import org.neo4j.graphdb.DynamicRelationshipType
import org.neo4j.graphdb.Node
import org.neo4j.graphdb.Direction
import scala.collection.JavaConversions._
import scala.language.implicitConversions
import util.Try
import viscel._

class ConfigNode(val self: Node) extends ViscelNode {

	def selfLabel = label.Config

	def version = Neo.txs { self[Int]("version") }

	def downloaded(size: Long): Unit = Neo.txs { self.setProperty("stat_download", downloaded + size) }

	def downloaded: Long = Neo.txs { self.get[Long]("stat_download").getOrElse(0L) }

}

object ConfigNode {
	def apply() = Neo.txs {
		Neo.node(label.Config, "id", "config")
			.getOrElse { Neo.create(label.Config, "id" -> "config", "version" -> 1) }
	}.pipe { new ConfigNode(_) }
}
