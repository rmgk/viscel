package viscel.store

import viscel.narration.Story
import viscel.database.Traversal
import viscel.store.coin.Collection

trait StoryCoin extends Coin {
	def story: Story
	def collection: Collection = Collection(Traversal.origin(self))
}
