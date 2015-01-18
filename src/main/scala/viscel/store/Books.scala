package viscel.store

import viscel.database.{Ntx, label}
import viscel.narration.{Narrator, Narrators}
import viscel.shared.Gallery
import viscel.shared.Story.Narration
import viscel.{Log, Viscel}

import scala.Predef.ArrowAssoc


object Books {


	def find(id: String)(implicit ntx: Ntx): Option[Book] =
		Viscel.time(s"find $id") { ntx.node(label.Collection, "id", id).map { Book.apply } }


	def findAndUpdate(narrator: Narrator)(implicit ntx: Ntx): Book = synchronized {
		ntx.db.beginTx().acquireWriteLock(Config.get().self)
		val col = find(narrator.id)
		col.foreach { c => c.name = narrator.name }
		col.getOrElse {
			Log.info(s"materializing $narrator")
			Book(ntx.create(label.Collection, "id" -> narrator.id, "name" -> narrator.name))
		}
	}

	def getNarration(id: String, deep: Boolean)(implicit ntx: Ntx): Option[Narration] = Narrators.get(id) match {
		case None => find(id).map(_.narration(deep))
		case Some(nar) => Some(findAndUpdate(nar).narration(deep))
	}

	def allNarrations(deep: Boolean)(implicit ntx: Ntx): List[Narration] = {
		val inDB = ntx.nodes(label.Collection).map { n => Book.apply(n).narration(deep) }.toList
		val dbids = inDB.map(_.id).toSet
		val other = Narrators.all.filterNot(n => dbids(n.id)).map { nar => Narration(nar.id, nar.name, 0, Gallery.empty, Nil) }.toList
		inDB ::: other
	}
}
