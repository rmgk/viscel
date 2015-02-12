package viscel.database

import viscel.narration.{Narrator, Narrators}
import viscel.shared.Description
import viscel.store.Config
import viscel.{Log, Viscel}

import scala.Predef.ArrowAssoc

object Books {


	def findExisting(id: String)(implicit ntx: Ntx): Option[Book] =
		Viscel.time(s"find $id") { ntx.node(label.Collection, "id", id).map { Book.apply } }


	def findAndUpdate(narrator: Narrator)(implicit ntx: Ntx): Book = synchronized {
		ntx.db.beginTx().acquireWriteLock(Config.get().self)
		val col = findExisting(narrator.id)
		col.foreach { c => c.name = narrator.name }
		col.getOrElse {
			Log.info(s"materializing $narrator")
			Book(ntx.create(label.Collection, "id" -> narrator.id, "name" -> narrator.name))
		}
	}

	def find(id: String)(implicit ntx: Ntx): Option[Book] = Narrators.get(id) match {
		case None => findExisting(id)
		case Some(nar) => Some(findAndUpdate(nar))
	}

	def allDescriptions()(implicit ntx: Ntx): List[Description] = {
		ntx.nodes(label.Collection).map { n => Book.apply(n).description() }.toList
	}
}
