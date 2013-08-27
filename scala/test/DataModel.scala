import org.scalatest._
import viscel.store._
import viscel._
import org.neo4j.graphdb._
import scala.collection.JavaConversions._

class DataModel extends FunSuite with BeforeAndAfterAll {

	test("config node sanity") {
		val cnode = ConfigNode()
		assert(cnode.version === 1, "version")
	}

	def relationshipsAllowed(node: Node, dir: Direction, relTypes: RelationshipType*) = assert(Neo.txs {
		node.getRelationships(dir).find(rel => !relTypes.exists(rel.isType(_)))
			.map { rel => s"""$node has unexpected incoming relation ${rel.getType.name} (allowed ${relTypes})""" }
	})

	def incomingAllowed(node: Node, relTypes: RelationshipType*) = relationshipsAllowed(node, Direction.INCOMING, relTypes: _*)
	def outgoingAllowed(node: Node, relTypes: RelationshipType*) = relationshipsAllowed(node, Direction.OUTGOING, relTypes: _*)

	test("reachable graph is sane") {
		val users = Neo.nodes(label.User).map { UserNode(_) }
		for (user <- users) {
			Neo.txs {
				incomingAllowed(user.self)
				outgoingAllowed(user.self, rel.bookmarked)
				user.bookmarks.foreach { bm =>
					incomingAllowed(bm.self, rel.next, rel.first, rel.last, rel.bookmarks)
					outgoingAllowed(bm.self, rel.next, rel.parent)

					val col = bm.collection
					val bmn = user.getBookmarkNode(col).get
					outgoingAllowed(col.self, rel.first, rel.last, rel.bookmark)
					incomingAllowed(col.self, rel.parent)
					outgoingAllowed(bmn, rel.bookmarks)
					incomingAllowed(bmn, rel.bookmark, rel.bookmarked)

					assert(bmn.to(rel.bookmarks).get === bm.self, "bookmark -> element")
					assert(bmn.from(rel.bookmarked).get === user.self, "user -> bookmark")
					assert(bmn.from(rel.bookmark).get === col.self, "col -> bookmark")
				}
			}
		}
	}

	override def afterAll(configMap: Map[String, Any]): Unit = {
		Neo.shutdown()
	}
}
