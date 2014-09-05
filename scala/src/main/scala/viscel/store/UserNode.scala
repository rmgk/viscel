package viscel.store

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.neo4j.graphdb.Node
import org.scalactic.TypeCheckedTripleEquals._

import scala.Predef.any2ArrowAssoc
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
class UserNode(val self: Node) extends ViscelNode with StrictLogging {
	def selfLabel = label.User

	def name = Neo.txs { self[String]("name") }
	def password = Neo.txs { self[String]("password") }

	def getBookmark(cn: CollectionNode) = Neo.txs { getBookmarkNode(cn).flatMap { bookmarkToElement } }

	def setBookmark(en: AssetNode): Unit = Neo.txts(s"create bookmark ${ en.collection.id }:${ en.position } for $name") {
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

	def bookmarks: Vector[AssetNode] = Neo.txs {
		self.outgoing(rel.bookmarked).map { _.getEndNode }.flatMap { bookmarkToElement }.toVector
	}

	def bookmarkToElement(bmn: Node): Option[AssetNode] = Neo.txs {
		bmn.to(rel.bookmarks) match {
			case Some(n) => Some(AssetNode(n))
			case None =>
				None
			//				for {
			//					chapter <- bmn.get[Int]("chapter")
			//					page <- bmn.get[Int]("page")
			//					ncol <- bmn.from(rel.bookmark)
			//					col = CollectionNode(ncol)
			//					en <- col(chapter).flatMap { cn => cn(page).orElse(cn.last) }.orElse(col.last.flatMap { _.last })
			//				} yield (chapter, page, col, en)
			//			} match {
			//				case None =>
			//					logger.warn(s"disconnected bookmark has insufficient information, delete")
			//					Neo.delete(bmn)
			//					None
			//				case Some((chapter, page, col, en)) =>
			//					logger.warn(s"rewire disconnected bookmark $name -> ${col.name}($chapter, $page)")
			//					bmn.createRelationshipTo(en.self, rel.bookmarks)
			//					bmn.removeProperty("chapter")
			//					bmn.removeProperty("page")
			//					Some(en)
		}
	}

	def deleteBookmark(cn: CollectionNode) = Neo.txts(s"delete bookmark ${ cn.id } for $name") {
		getBookmarkNode(cn).foreach { bmn =>
			bmn.getRelationships.asScala.foreach { _.delete }
			bmn.delete()
		}
	}

	def getBookmarkNode(cn: CollectionNode): Option[Node] = Neo.txs {
		cn.self.outgoing(rel.bookmark).view.map { _.getEndNode }.find { bmn => bmn.from(rel.bookmarked).get === this.self }
	}

}

object UserNode {
	def apply(node: Node) = new UserNode(node)
	def apply(name: String) = Neo.node(label.User, "name", name).map { new UserNode(_) }

	def create(name: String, password: String) = UserNode(Neo.create(label.User, "name" -> name, "password" -> password))
}
