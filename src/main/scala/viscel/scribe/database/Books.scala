package viscel.scribe.database

import viscel.scribe.database.Implicits.NodeOps
import viscel.scribe.narration.{Narrator, Page}
import viscel.scribe.store.Config
import viscel.scribe.{Log, Scribe}

import scala.Predef.ArrowAssoc

class Books(neo: Neo) {

	def findExisting(id: String): Option[Book] =
		Scribe.time(s"find $id") {
			neo.tx { implicit ntx =>
				ntx.node(label.Book, "id", id).map { Book.apply }
			}
		}


	def findAndUpdate(narrator: Narrator): Book = findAndUpdate(narrator.id, narrator.name)

	def findAndUpdate(id: String, name: String): Book = synchronized {
		neo.tx { implicit ntx =>
			ntx.db.beginTx().acquireWriteLock(Config.get().self)
			val col = findExisting(id)
			col.foreach { c => c.name = name }
			col.getOrElse {
				Log.info(s"materializing $id($name)")
				Book(ntx.create(label.Book, "id" -> id, "name" -> name))
			}
		}
	}

	def all(): List[Book] = neo.tx { implicit ntx =>
		ntx.nodes(label.Book).map { n => Book.apply(n) }.toList
	}

	def importFlat(id: String, name: String, pages: List[Page]) = neo.tx { implicit ntx =>
		val book = findAndUpdate(id, name)
		if (Archive.applyNarration(book.self, pages.map(_.asset))) book.invalidateSize()
		book.self.layerBelow.zip(pages.map(_.blob)).foreach {
			case (node, Some(blob)) => node.to_=(rel.blob, Codec.create(blob))
			case _ =>
		}
	}
}
