package viscel.store

import org.neo4j.graphdb.Node
import org.scalactic.{Bad, ErrorMessage, Good, Or, attempt}
import spray.http.MediaType
import viscel.narration.Narrator
import viscel.crawler.AbsUri
import viscel.description.Story
import viscel.store.coin.{Asset, Blob, Chapter, Collection, Config, Core, Page, User}

import scala.Predef.any2ArrowAssoc
import scala.collection.JavaConverters.iterableAsScalaIterableConverter


object Vault {

	def wrap(node: Node): Coin Or ErrorMessage = node.getLabels.asScala.to[List] match {
		case Nil => Bad(s"unlabeled node: $node")
		case List(l) => label.SimpleLabel(l) match {
			case label.Asset => Good(Asset(node))
			case label.Chapter => Good(Chapter(node))
			case label.Collection => Good(Collection(node))
			case label.Config => Good(Config(node))
			case label.Blob => Good(Blob(node))
			case label.Core => Good(Core(node))
			case label.Page => Good(Page(node))
			case label.User => Good(User(node))
			case label.Unlabeled => Bad(s"explicit unlabeled nodes are not allowed in database: $node")
		}
		case list@_ :: _ => Bad(s"to many labels $list for $node")
	}

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

	def byID(id: Long)(implicit neo: Neo): Coin Or ErrorMessage =
		attempt { neo.db.getNodeById(id) }.fold(wrap, t => Bad(t.toString))

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

	}

	object update {
		def core(desc: Story.Core)(implicit neo: Neo): coin.Core = neo.txs {
			neo.node(label.Core, "id", desc.id) match {
				case None => create.core(desc)
				case Some(node) =>
					node.getPropertyKeys.asScala.foreach(node.removeProperty)
					(Metadata.prefix(desc.metadata) + ("name" -> desc.name) + ("id" -> desc.id) + ("kind" -> desc.kind)).foreach { case (k, v) => node.setProperty(k, v) }
					Core(node)
			}
		}

		def collection(narrator: Narrator)(implicit neo: Neo): Collection = neo.txs {
			val col = Vault.find.collection(narrator.id)
			col.foreach(_.name = narrator.name)
			col.getOrElse { create.collection(narrator.id, narrator.name) }
		}

	}
}
