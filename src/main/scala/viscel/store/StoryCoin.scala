package viscel.store

import viscel.database.{Ntx, Traversal}
import viscel.shared.Story
import viscel.store.coin.Collection

trait StoryCoin extends Any with Coin {
	def story(implicit neo: Ntx): Story
}
