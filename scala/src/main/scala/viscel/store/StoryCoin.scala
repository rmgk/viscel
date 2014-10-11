package viscel.store

import viscel.narration.Story
import viscel.database.{Ntx, Traversal}
import viscel.store.coin.Collection

trait StoryCoin extends Coin {
	def story(implicit neo: Ntx): Story
	def collection(implicit neo: Ntx): Collection = Collection(Traversal.origin(self))
}
