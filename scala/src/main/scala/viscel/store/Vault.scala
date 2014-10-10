package viscel.store

import org.neo4j.graphdb.Node
import spray.http.MediaType
import viscel.crawler.AbsUri
import viscel.narration.Story.{Failed, More}
import viscel.narration.{Narrator, Story}
import viscel.store.coin.{Asset, Blob, Chapter, Collection, Config, Core, Page, User}

import scala.Predef.any2ArrowAssoc


object Vault {

	@volatile private var configCache: Config = _
	def config()(implicit neo: Neo): Config = {
		// yes, this has a race condition, but the database transaction guarantees, that only one config is generated
		// and the worst that could happen is an unnecessary lookup (also config is probably accessed on startup, before any multithreading nonsense)
		if (configCache == null) {
			configCache = neo.txs {
				Config(neo.node(label.Config, "id", "config").getOrElse {
					neo.create(label.Config, "id" -> "config", "version" -> 1)
				})
			}
		}
		configCache
	}

	object find {
		def user(name: String)(implicit neo: Neo): Option[User] =
			neo.node(label.User, "name", name).map { User.apply }

		def collection(id: String)(implicit neo: Neo): Option[Collection] =
			neo.node(label.Collection, "id", id).map { Collection.apply }

		def blob(source: AbsUri)(implicit neo: Neo): Option[Blob] =
			neo.node(label.Blob, "source", source.toString(), logTime = false).map { Blob.apply }

	}

	object create {
		def user(name: String, password: String)(implicit neo: Neo): User =
			User(neo.create(label.User, "name" -> name, "password" -> password))

		def collection(id: String, name: String)(implicit neo: Neo): Collection =
			Collection(neo.create(label.Collection, "id" -> id, "name" -> name))

		def blob(sha1: String, mediatype: MediaType, source: AbsUri)(implicit neo: Neo): Blob =
			Blob(Neo.create(label.Blob, "sha1" -> sha1, "mediatype" -> mediatype.value, "source" -> source.toString()))

		def fromStory(desc: Story)(implicit neo: Neo): Node = desc match {
			case More(loc, pagetype) => neo.create(label.Page, "location" -> loc.toString, "pagetype" -> pagetype)
			case Story.Chapter(name, metadata) => neo.create(label.Chapter, Metadata.prefix(metadata) + ("name" -> name))
			case Story.Asset(source, origin, metadata) => neo.create(label.Asset, Metadata.prefix(metadata) + ("source" -> source.toString) + ("origin" -> origin.toString))
			case Story.Core(kind, id, name, metadata) => neo.create(label.Core, Metadata.prefix(metadata) + ("id" -> id) + ("kind" -> kind) + ("name" -> name))
			case Failed(reason) => throw new IllegalArgumentException(reason.toString())
		}

	}

	object update {
		def collection(narrator: Narrator)(implicit neo: Neo): Collection = neo.txs {
			val col = Vault.find.collection(narrator.id)
			col.foreach(_.name = narrator.name)
			col.getOrElse { create.collection(narrator.id, narrator.name) }
		}

	}
}
