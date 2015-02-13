package viscel.database

import viscel.narration.{Description, Narrator}
import viscel.store.Config
import viscel.{Log, Viscel}

import scala.Predef.ArrowAssoc

object Books {


	def findExisting(id: String)(implicit ntx: Ntx): Option[Book] =
		Viscel.time(s"find $id") { ntx.node(label.Book, "id", id).map { Book.apply } }


	def findAndUpdate(narrator: Narrator)(implicit ntx: Ntx): Book = synchronized {
		ntx.db.beginTx().acquireWriteLock(Config.get().self)
		val col = findExisting(narrator.id)
		col.foreach { c => c.name = narrator.name }
		col.getOrElse {
			Log.info(s"materializing $narrator")
			Book(ntx.create(label.Book, "id" -> narrator.id, "name" -> narrator.name))
		}
	}

	def allDescriptions()(implicit ntx: Ntx): List[Description] = {
		ntx.nodes(label.Book).map { n => Book.apply(n).description() }.toList
	}
}
