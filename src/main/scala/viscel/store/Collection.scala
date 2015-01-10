package viscel.store

import org.neo4j.graphdb.{ConstraintViolationException, Node}
import org.scalactic.TypeCheckedTripleEquals._
import viscel.{Log, Viscel}
import viscel.database.Implicits.NodeOps
import viscel.database.{NeoCodec, Ntx, label}
import viscel.narration.Narrator
import viscel.shared.Story.{Asset, Chapter, Narration}
import viscel.shared.{Gallery, Story}

import scala.Predef.ArrowAssoc
import scala.collection.JavaConverters.iterableAsScalaIterableConverter

final case class Collection(self: Node) extends AnyVal {

	def id(implicit ntx: Ntx): String = self.prop[String]("id")
	def name(implicit ntx: Ntx): String = self.get[String]("name").getOrElse(id)
	def name_=(value: String)(implicit neo: Ntx) = self.setProperty("name", value)
	def size(implicit ntx: Ntx): Int = {
		val sopt = self.get[Int]("size")
		sopt.getOrElse {
			val size = Collection.size(this)
			self.setProperty("size", size)
			size
		}
	}


	def narration(deep: Boolean)(implicit ntx: Ntx): Narration = {
		def allAssets(node: Node): (Int, List[Story.Asset], List[(Int, Story.Chapter)]) = {
			node.fold((0, List[Story.Asset](), List[(Int, Story.Chapter)]())) {
				case state@(pos, assets, chapters) => NeoCodec.load[Story](_) match {
					case asset@Asset(_, _, _, _) => (pos + 1, asset :: assets, chapters)
					case chapter@Chapter(_, _) => (pos, assets, (pos, chapter) :: chapters)
					case _ => state
				}
			}
		}

		if (deep) {
			val (size, assets, chapters) = allAssets(self)
			Narration(id, name, assets.size, Gallery.fromList(assets.reverse), chapters)
		}
		else {
			Narration(id, name, size, Gallery.empty, Nil)
		}
	}

}

object Collection {
	def size(col: Collection)(implicit ntx: Ntx): Int = col.self.fold(0)(s => {
		case n if n.hasLabel(label.Asset) => s + 1
		case _ => s
	})

	def find(id: String)(implicit ntx: Ntx): Option[Collection] =
		Viscel.time (s"find $id") { ntx.node(label.Collection, "id", id).map { Collection.apply } }


	def findAndUpdate(narrator: Narrator)(implicit ntx: Ntx): Collection = synchronized {
		ntx.db.beginTx().acquireWriteLock(Config.get().self)
		val col = find(narrator.id)
		col.foreach { c => c.name = narrator.name }
		col.getOrElse {
			Log.info(s"materializing $narrator")
			Collection(ntx.create(label.Collection, "id" -> narrator.id, "name" -> narrator.name))
		}
	}

	def getNarration(id: String, deep: Boolean)(implicit ntx: Ntx): Option[Narration] =	Narrator.get(id) match {
		case None => find(id).map(_.narration(deep))
		case Some(nar) => Some(findAndUpdate(nar).narration(deep))
	}

	def allNarrations(deep: Boolean)(implicit ntx: Ntx): List[Narration] = {
		val inDB = ntx.nodes(label.Collection).map { n => Collection.apply(n).narration(deep) }.toList
		val dbids = inDB.map(_.id).toSet
		val other = Narrator.all.filterNot(n => dbids(n.id)).map { nar => Narration(nar.id, nar.name, 0, Gallery.empty, Nil)}.toList
		inDB ::: other
	}
}
