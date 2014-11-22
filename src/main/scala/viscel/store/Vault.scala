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

	object find {
		def collection(id: String)(implicit neo: Ntx): Option[Collection] =
			neo.node(label.Collection, "id", id).map { Collection.apply }

	}

	object update {
		def collection(narrator: Narrator)(implicit ntx: Ntx): Collection = {
			val col = Vault.find.collection(narrator.id)
			col.foreach(_.name = narrator.name)
			col.getOrElse { Collection(ntx.create(label.Collection, "id" -> narrator.id, "name" -> narrator.name)) }
		}

	}
}
