package viscel.store.coin

import org.neo4j.graphdb.Node
import org.scalactic.TypeCheckedTripleEquals._
import viscel.store._

import scala.collection.JavaConverters._
import scala.language.implicitConversions

/**
 * A user node currently encodes the bookmarks of the user.
 *
 * If a user `u` has a bookmark `b` to the element `e` of collection `c`
 * the graph will look somewhat like this
 *
 * (u) -[:bookmarked]-> (b)
 * (c) -[:bookmark]-> (b) -[:bookmarks]-> (e)
 */
final case class User(self: Node) extends Coin {
	def name: String = Neo.txs { self[String]("name") }
	def password: String = Neo.txs { self[String]("password") }

	def getBookmark(cn: Collection): Option[Asset] = Neo.txs { getBookmarkNode(cn).flatMap { bookmarkToAsset } }

	def setBookmark(en: Asset): Unit = Neo.txts(s"create bookmark ${ en.collection.id }:${ en.position } for $name") {
		def createBookmark() = {
			val bmn = Neo.create(label.Bookmark)
			self.createRelationshipTo(bmn, rel.bookmarked)
			en.collection.self.createRelationshipTo(bmn, rel.bookmark)
			bmn
		}
		val bmn = getBookmarkNode(en.collection).getOrElse(createBookmark())
		bmn.outgoing(rel.bookmarks).foreach { _.delete }
		bmn.createRelationshipTo(en.self, rel.bookmarks)
	}

	def bookmarks: Vector[Asset] = Neo.txs {
		self.outgoing(rel.bookmarked).map { _.getEndNode }.flatMap { bookmarkToAsset }.toVector
	}

	def bookmarkToAsset(bmn: Node): Option[Asset] = Neo.txs {
		bmn.to(rel.bookmarks) match {
			case Some(n) => Some(Asset(n))
			case None =>
				None
		}
	}

	def deleteBookmark(cn: Collection) = Neo.txts(s"delete bookmark ${ cn.id } for $name") {
		getBookmarkNode(cn).foreach { bmn =>
			bmn.getRelationships.asScala.foreach { _.delete }
			bmn.delete()
		}
	}

	def getBookmarkNode(cn: Collection): Option[Node] = Neo.txs {
		cn.self.outgoing(rel.bookmark).view.map { _.getEndNode }.find { bmn => bmn.from(rel.bookmarked).get === this.self }
	}

}
