package viscel.store

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.neo4j.graphdb.Node
import viscel.store.coin.{Core, Chapter, Page, Asset}
import viscel.store.label.SimpleLabel


trait Coin extends StrictLogging {
	def self: Node
	def nid: Long = self.getId
}

object Coin {
	class CheckNode[N](label: SimpleLabel, f: Node => N) extends (Node => Option[N]) {
		def unapply(node: Node): Option[N] = if (node.hasLabel(label)) Some(f(node)) else None
		override def apply(node: Node): Option[N] = unapply(node)
	}

	object isAsset extends CheckNode(label.Asset, Asset.apply)
	object isPage extends CheckNode(label.Page, Page.apply)
	object isChapter extends CheckNode(label.Chapter, Chapter.apply)
	object isCore extends CheckNode(label.Core, Core.apply)
	object hasStory extends (Node => Option[StoryCoin]) {
		def unapply(node: Node): Option[StoryCoin] = isAsset(node).orElse(isPage(node)).orElse(isChapter(node)).orElse(isCore(node))
		def apply(node: Node): Option[StoryCoin] = unapply(node)
	}
}
