package viscel.store

import org.neo4j.graphdb.Node
import viscel.description.Story
import viscel.store.label.SimpleLabel
import viscel.store.coin.{Core, Page, Asset, Chapter, Collection}

import scala.collection.JavaConverters._



abstract class StoryCoin extends Coin {
	def story: Story
	def collection: Collection = Collection(Traversal.origin(self))
}
