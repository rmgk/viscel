package viscel.store

import org.neo4j.graphdb.Node
import viscel.database.{Ntx, label}
import viscel.narration.Narrator
import viscel.shared.{AbsUri, Story}
import viscel.store.coin.{Blob, Collection, Config}

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

		def blob(source: AbsUri)(implicit neo: Ntx): Option[Blob] =
			neo.node(label.Blob, "source", source.toString(), logTime = false).map { Blob.apply }

	}

	object create {

		def fromStory(desc: Story)(implicit neo: Ntx): Node = desc match {
			case Story.Chapter(name, metadata) => neo.create(label.Chapter, Metadata.prefix(metadata) + ("name" -> name))
			case Story.Asset(source, origin, metadata, blob) => neo.create(label.Asset, Metadata.prefix(metadata) + ("source" -> source.toString) + ("origin" -> origin.toString))
			case Story.Core(kind, id, name, metadata) => neo.create(label.Core, Metadata.prefix(metadata) + ("id" -> id) + ("kind" -> kind) + ("name" -> name))
			case Story.Failed(reason) => throw new IllegalArgumentException(reason.toString())
			case Story.More(loc, pagetype, layer) => neo.create(label.Page, "location" -> loc.toString, "pagetype" -> pagetype)
			case Story.Blob(sha1, mediastring) => neo.create(label.Blob, "sha1" -> sha1, "mediatype" -> mediastring)
		}

	}

	object update {
		def collection(narrator: Narrator)(implicit ntx: Ntx): Collection = {
			val col = Vault.find.collection(narrator.id)
			col.foreach(_.name = narrator.name)
			col.getOrElse { Collection(ntx.create(label.Collection, "id" -> narrator.id, "name" -> narrator.name)) }
		}

	}
}
