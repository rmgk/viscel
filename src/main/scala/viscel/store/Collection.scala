package viscel.store

import org.neo4j.graphdb.Node
import viscel.database.Traversal.findForward
import viscel.database.{NodeOps, Ntx, Traversal, label}
import viscel.narration.Narrator
import viscel.shared.{Gallery, Story}
import viscel.shared.Story.Narration
import viscel.store.Coin.CheckNode

import scala.Predef.any2ArrowAssoc

final case class Collection(self: Node) extends AnyVal {

	def id(implicit neo: Ntx): String = self[String]("id")
	def name(implicit neo: Ntx): String = self.get[String]("name").getOrElse(id)
	def name_=(value: String)(implicit neo: Ntx) = self.setProperty("name", value)

	def first(implicit neo: Ntx): Option[Coin.Asset] = findForward(Coin.isAsset)(self)

	def size(implicit neo: Ntx): Int = Traversal.fold(0, self)(count => {
		case Coin.isAsset(a) => count + 1
		case _ => count
	})

	def narration(nested: Boolean)(implicit neo: Ntx): Narration = {
		def allAssets(node: Node): List[Story.Asset] = {
			Traversal.fold(List[Story.Asset](), node) { state => {
				case Coin.isAsset(asset) => asset.story(nested = true) :: state
				case _ => state
			}
			}
		}

		if (nested) {
			val assets = allAssets(self).reverse
			Narration(id, name, assets.size, Gallery.fromList(assets))
		}
		else {
			Narration(id, name, size, Gallery.fromList(Nil))
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