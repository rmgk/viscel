package viscel.store

import org.neo4j.graphdb.Node
import viscel.database.{Ntx, label}
import viscel.narration.Narrator
import viscel.shared.Story
import viscel.store.Coin.Metadata

import scala.Predef.any2ArrowAssoc


object Vault {

	def config()(implicit neo: Ntx): Config = synchronized {
		Config(neo.node(label.Config, "id", "config").getOrElse {
			neo.create(label.Config, "id" -> "config", "version" -> 1)
		})
	}


}
