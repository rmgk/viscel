package viscel.store

import org.neo4j.graphdb.Node
import viscel.database.Implicits.NodeOps
import viscel.database.{NeoCodec, Ntx, label}
import viscel.narration.Narrator
import viscel.shared.Story.{Chapter, Asset, Narration}
import viscel.shared.{Gallery, Story}

import scala.Predef.ArrowAssoc

final case class Collection(self: Node) extends AnyVal {

	def id(implicit neo: Ntx): String = self.prop[String]("id")
	def name(implicit neo: Ntx): String = self.get[String]("name").getOrElse(id)
	def name_=(value: String)(implicit neo: Ntx) = self.setProperty("name", value)


	def narration(deep: Boolean)(implicit neo: Ntx): Narration = {
		def allAssets(node: Node): (Int, List[Story.Asset], List[(Int, Story.Chapter)]) = {
			node.fold((0, List[Story.Asset](), List[(Int, Story.Chapter)]())) {
				case state@(pos, assets, chapters) => NeoCodec.load[Story](_) match {
					case asset @ Asset(_, _, _, _) => (pos + 1, asset :: assets, chapters)
					case chapter @ Chapter(_, _) => (pos, assets, (pos, chapter) :: chapters)
					case _ => state
				}
			}
		}

		if (deep) {
			val (size, assets, chapters) = allAssets(self)
			Narration(id, name, Gallery.fromList(assets.reverse), chapters)
		}
		else {
			Narration(id, name, Gallery.fromList(Nil), Nil)
		}
	}

}

object Collection {
	def find(id: String)(implicit neo: Ntx): Option[Collection] =
		neo.node(label.Collection, "id", id).map { Collection.apply }

	def findAndUpdate(narrator: Narrator)(implicit ntx: Ntx): Collection = synchronized {
		val col = find(narrator.id)
		col.foreach(_.name = narrator.name)
		col.getOrElse { Collection(ntx.create(label.Collection, "id" -> narrator.id, "name" -> narrator.name)) }
	}
}
