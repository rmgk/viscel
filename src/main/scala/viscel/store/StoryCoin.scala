package viscel.store

import viscel.database.{Ntx, Traversal}
import viscel.shared.Story

trait StoryCoin extends Any with Coin {
	def story(implicit neo: Ntx): Story
}
