package viscel.store

import spray.http.MediaType
import viscel.narration.{Story, Narrator}
import viscel.crawler.AbsUri
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

		def page(desc: Story.More)(implicit neo: Neo): Page =
			Page(Neo.create(label.Page, "location" -> desc.loc.toString, "pagetype" -> desc.pagetype))

		def core(desc: Story.Core)(implicit neo: Neo): coin.Core =
			Core(Neo.create(label.Core, Metadata.prefix(desc.metadata) + ("id" -> desc.id) + ("kind" -> desc.kind) + ("name" -> desc.name)))

		def chapter(desc: Story.Chapter)(implicit neo: Neo): Chapter =
			Chapter(Neo.create(label.Chapter, Metadata.prefix(desc.metadata) + ("name" -> desc.name)))

		def asset(asset: Story.Asset)(implicit neo: Neo): Asset =
			Asset(Neo.create(label.Asset, Metadata.prefix(asset.metadata) + ("source" -> asset.source.toString) + ("origin" -> asset.origin.toString)))

		def blob(sha1: String, mediatype: MediaType, source: AbsUri)(implicit neo: Neo): Blob =
			Blob(Neo.create(label.Blob, "sha1" -> sha1, "mediatype" -> mediatype.value, "source" -> source.toString()))

		def fromStory(desc: Story)(implicit neo: Neo): StoryCoin = {
			desc match {
				case Story.Failed(reason) => throw new IllegalArgumentException(reason.toString())
				case pointer@Story.More(_, _) => page(pointer)
				case chap@Story.Chapter(_, _) => chapter(chap)
				case asset@Story.Asset(_, _, _) => Vault.create.asset(asset)
				case core@Story.Core(_, _, _, _) => Vault.create.core(core)
			}
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
