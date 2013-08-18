package viscel.store

import com.typesafe.scalalogging.slf4j.Logging
import org.neo4j.cypher.ExecutionEngine
import org.neo4j.graphdb.DynamicLabel
import org.neo4j.graphdb.DynamicRelationshipType
import org.neo4j.graphdb.Node
import org.neo4j.graphdb.Direction
import scala.collection.JavaConversions._
import scala.language.implicitConversions
import viscel.Element
import util.Try

class Collection(val id: String) {

	import Collection._

	implicit def stringToRelationship(name: String) = DynamicRelationshipType.withName(name)

	val main = Neo.tx{_.findNodesByLabelAndProperty(labelCollection, "id", id).toList.head}

	def last = Neo.execute("""
		|start main = node({main})
		|match (main) -[:last]-> (page: Page)
		|return page
		""",
		"main" -> main
		).columnAs[Node]("page").toList.headOption

	def first = Neo.execute("""
		|start main = node({main})
		|match (main) -[:first]-> (page: Page)
		|return page
		""",
		"main" -> main
		).columnAs[Node]("page").toList.headOption

	def size = Neo.execute("""
		|start main = node({main})
		|match (main) -[first?:first]-> () -[r:next*]-> () <-[?:last]- (main)
		|return case
		|when first is null THEN 0
		|else length(r) + 1
		|end as length
		""".stripMargin.trim,
		"main" -> main
		).columnAs[Long]("length").next

	def add(element: Element, pred: Option[Node] = None) = Neo.tx { db =>
		val node = db.createNode(labelPage)
		element.toMap.foreach{case (k,v) => node.setProperty(k, v)}
		pred.orElse(last) match {
			case Some(p) => {
				p.getSingleRelationship("last", Direction.INCOMING).delete
				p.createRelationshipTo(node, "next")
				main.createRelationshipTo(node, "last")
			}
			case None => {
				main.createRelationshipTo(node, "first")
				main.createRelationshipTo(node, "last")
			}
		}
		node
	}

}


object Collection {

	val labelCollection = DynamicLabel.label( "Collection" )
	val labelLegacy = DynamicLabel.label( "Legacy" )
	val labelPage = DynamicLabel.label( "Page" )

	def advance(step: Int)(start: Node*) = Neo.execute(s"""
		|start main = node({main})
		|match (main) -[r:next * $step]-> (page: Page)
		|return page
		""",
		"main" -> start
		).columnAs[Node]("page").toIndexedSeq

	def create(id: String, name: Option[String] = None) = Neo.tx { db =>
		val node = db.createNode(labelCollection)
		node.setProperty("id", id)
		name.foreach(node.setProperty("name", _))
		node
	}

	def apply(id: String) = new Collection(id)

	def list = Neo.execute("""
		|match (col: Collection)
		|return col.id
		""").columnAs[String]("col.id").toList
}
