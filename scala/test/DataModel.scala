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
			.map { rel => s"""$node (${node.getLabels.toList}) has unexpected incoming relation ${rel.getType.name} (allowed ${relTypes})""" }
	})

	def incomingAllowed(node: Node, relTypes: RelationshipType*) = relationshipsAllowed(node, Direction.INCOMING, relTypes: _*)
	def outgoingAllowed(node: Node, relTypes: RelationshipType*) = relationshipsAllowed(node, Direction.OUTGOING, relTypes: _*)

	def assertSaneUser(user: UserNode) = {
		incomingAllowed(user.self)
		outgoingAllowed(user.self, rel.bookmarked)
	}

	def assertSaneElement(en: ElementNode) = {
		incomingAllowed(en.self, rel.next, rel.first, rel.last, rel.bookmarks)
		outgoingAllowed(en.self, rel.next, rel.parent)
	}

	def assertSaneBookmark(user: UserNode, col: CollectionNode, bm: ElementNode) = {
		val bmn = user.getBookmarkNode(col).get
		outgoingAllowed(bmn, rel.bookmarks)
		incomingAllowed(bmn, rel.bookmark, rel.bookmarked)

		assert(bmn.to(rel.bookmarks).get === bm.self, "bookmark -> element")
		assert(bmn.from(rel.bookmarked).get === user.self, "user -> bookmark")
		assert(bmn.from(rel.bookmark).get === col.self, "col -> bookmark")
	}

	def assertSaneCollection(col: CollectionNode) = {
		outgoingAllowed(col.self, rel.first, rel.last, rel.bookmark)
		incomingAllowed(col.self, rel.parent)
		col.first match {
			case Some(first) => assertSaneElementList(first, col)
			case None => fail("empty collection can not be reachable")
		}
	}

	def assertSaneElementList(en: ElementNode, col: CollectionNode): Unit = {
		assertSaneElement(en)
		assert(en.collection === col, "element in list has correct collection")
		en.next match {
			case Some(next) =>
				assert(next.position === en.position + 1, "position is increasing")
				assertSaneElementList(next, col)
			case None => assert(col.last.get === en, "last is correct")
		}
	}

	test("reachable graph is sane") {
		val users = Neo.nodes(label.User).map { UserNode(_) }
		val collections = Neo.txs {
			for {
				user <- users
				if { assertSaneUser(user); true }
				bm <- user.bookmarks
			} yield {
				assertSaneElement(bm)
				val col = bm.collection
				assertSaneBookmark(user, col, bm)
				col
			}
		}.toSet

		for (col <- collections) {
			Neo.txs {
				assertSaneCollection(col)
			}
		}
	}

	override def afterAll(configMap: Map[String, Any]): Unit = {
		Neo.shutdown()
	}
}
