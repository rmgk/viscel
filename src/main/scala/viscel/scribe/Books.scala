package viscel.scribe

import viscel.scribe.database.{Neo, label}
import viscel.scribe.narration.Narrator
import viscel.scribe.store.Config

import scala.Predef.ArrowAssoc

class Books(neo: Neo) {

	def findExisting(id: String): Option[Book] =
		Scribe.time(s"find $id") {
			neo.tx { implicit ntx =>
				ntx.node(label.Book, "id", id).map { Book.apply }
			}
		}


	def findAndUpdate(narrator: Narrator): Book = synchronized {
		neo.tx { implicit ntx =>
			ntx.db.beginTx().acquireWriteLock(Config.get().self)
			val col = findExisting(narrator.id)
			col.foreach { c => c.name = narrator.name }
			col.getOrElse {
				Log.info(s"materializing $narrator")
				Book(ntx.create(label.Book, "id" -> narrator.id, "name" -> narrator.name))
			}
		}
	}

	def all(): List[Book] = neo.tx { implicit ntx =>
		ntx.nodes(label.Book).map { n => Book.apply(n) }.toList
	}
}
