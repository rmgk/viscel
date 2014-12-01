package viscel.store

import org.neo4j.graphdb.Node
import viscel.database.{NodeOps, Ntx, label}
import viscel.narration.Narrator
import viscel.shared.Story.Narration
import viscel.shared.{Gallery, Story}
import viscel.store.Coin.CheckNode

import scala.Predef.ArrowAssoc

final case class Collection(self: Node) extends AnyVal {

	def id(implicit neo: Ntx): String = self.prop[String]("id")
	def name(implicit neo: Ntx): String = self.get[String]("name").getOrElse(id)
	def name_=(value: String)(implicit neo: Ntx) = self.setProperty("name", value)

	def size(implicit neo: Ntx): Int = self.fold(0)(count => {
		case Coin.isAsset(a) => count + 1
		case _ => count
	})

	def narration(deep: Boolean)(implicit neo: Ntx): Narration = {
		def allAssets(node: Node): (Int, List[Story.Asset], List[(Int, Story.Chapter)]) = {
			node.fold((0, List[Story.Asset](), List[(Int, Story.Chapter)]())) {
				case state@(pos, assets, chapters) => {
					case Coin.isAsset(asset) => (pos + 1, asset.story :: assets, chapters)
					case Coin.isChapter(chapter) => (pos, assets, (pos, chapter.story) :: chapters)
					case _ => state
				}
			}
		}

		if (deep) {
			val (size, assets, chapters) = allAssets(self)
			Narration(id, name, size, Gallery.fromList(assets.reverse), chapters)
		}
		else {
			Narration(id, name, size, Gallery.fromList(Nil), Nil)
		}
	}

}

object Collection {
	object isCollection extends CheckNode(label.Collection, Collection.apply)

	def find(id: String)(implicit neo: Ntx): Option[Collection] =
		neo.node(label.Collection, "id", id).map { Collection.apply }


	def findAndUpdate(narrator: Narrator)(implicit ntx: Ntx): Collection = {
		val col = find(narrator.id)
		col.foreach(_.name = narrator.name)
		col.getOrElse { Collection(ntx.create(label.Collection, "id" -> narrator.id, "name" -> narrator.name)) }
	}
}
