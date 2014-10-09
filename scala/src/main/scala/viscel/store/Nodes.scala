package viscel.store

import org.neo4j.graphdb.Node
import org.scalactic._
import spray.http.MediaType
import viscel.cores.Core
import viscel.crawler.AbsUri
import viscel.description.{Asset, Chapter, CoreDescription, Pointer}
import viscel.store.nodes._

import scala.Predef.any2ArrowAssoc
import scala.collection.JavaConverters._


object Nodes {

	def wrap(node: Node): ViscelNode Or ErrorMessage = node.getLabels.asScala.to[List] match {
		case Nil => Bad(s"unlabeled node: $node")
		case List(l) => label.SimpleLabel(l) match {
			case label.Asset => Good(AssetNode(node))
			case label.Chapter => Good(ChapterNode(node))
			case label.Collection => Good(CollectionNode(node))
			case label.Config => Good(ConfigNode(node))
			case label.Blob => Good(BlobNode(node))
			case label.Core => Good(CoreNode(node))
			case label.Page => Good(PageNode(node))
			case label.User => Good(UserNode(node))
			case label.Unlabeled => Bad(s"explicit unlabeled nodes are not allowed in database: $node")
		}
		case list@_ :: _ => Bad(s"to many labels $list for $node")
	}

	@volatile private var configCache: ConfigNode = _
	def config()(implicit neo: Neo): ConfigNode = {
		// yes, this has a race condition, but the database transaction guarantees, that only one config is generated
		// and the worst that could happen is an unnecessary lookup (also config is probably accessed on startup, before any multithreading nonsense)
		if (configCache == null) {
			configCache = neo.txs {
				ConfigNode(neo.node(label.Config, "id", "config").getOrElse {
					neo.create(label.Config, "id" -> "config", "version" -> 1)
				})
			}
		}
		configCache
	}

	def byID(id: Long)(implicit neo: Neo): ViscelNode Or ErrorMessage =
		attempt { neo.db.getNodeById(id) }.fold(wrap, t => Bad(t.toString))

	object find {
		def user(name: String)(implicit neo: Neo): Option[UserNode] =
			neo.node(label.User, "name", name).map { UserNode.apply }

		def collection(id: String)(implicit neo: Neo): Option[CollectionNode] =
			neo.node(label.Collection, "id", id).map { CollectionNode.apply }

		def blob(source: AbsUri)(implicit neo: Neo): Option[BlobNode] =
			neo.node(label.Blob, "source", source.toString(), logTime = false).map { BlobNode.apply }

	}


	object create {
		def user(name: String, password: String)(implicit neo: Neo): UserNode =
			UserNode(neo.create(label.User, "name" -> name, "password" -> password))

		def collection(id: String, name: String)(implicit neo: Neo): CollectionNode =
			CollectionNode(neo.create(label.Collection, "id" -> id, "name" -> name))

		def page(desc: Pointer)(implicit neo: Neo): PageNode =
			PageNode(Neo.create(label.Page, "location" -> desc.loc.toString, "pagetype" -> desc.pagetype))

		def core(desc: CoreDescription)(implicit neo: Neo): CoreNode =
			CoreNode(Neo.create(label.Core, Metadata.prefix(desc.metadata) + ("id" -> desc.id) + ("kind" -> desc.kind) + ("name" -> desc.name)))

		def chapter(desc: Chapter)(implicit neo: Neo): ChapterNode =
			ChapterNode(Neo.create(label.Chapter, Metadata.prefix(desc.metadata) + ("name" -> desc.name)))

		def asset(asset: Asset)(implicit neo: Neo): AssetNode =
			AssetNode(Neo.create(label.Asset, Metadata.prefix(asset.metadata) + ("source" -> asset.source.toString) + ("origin" -> asset.origin.toString)))

		def blob(sha1: String, mediatype: MediaType, source: AbsUri)(implicit neo: Neo): BlobNode =
			BlobNode(Neo.create(label.Blob, "sha1" -> sha1, "mediatype" -> mediatype.value, "source" -> source.toString()))

	}

	object update {
		def core(desc: CoreDescription)(implicit neo: Neo): CoreNode = neo.txs {
			neo.node(label.Core, "id", desc.id) match {
				case None => create.core(desc)
				case Some(node) =>
					node.getPropertyKeys.asScala.foreach(node.removeProperty)
					(Metadata.prefix(desc.metadata) + ("name" -> desc.name) + ("id" -> desc.id) + ("kind" -> desc.kind)).foreach { case (k, v) => node.setProperty(k, v) }
					CoreNode(node)
			}
		}

		def collection(core: Core)(implicit neo: Neo): CollectionNode = neo.txs {
			val col = Nodes.find.collection(core.id)
			col.foreach(_.name = core.name)
			col.getOrElse { create.collection(core.id, core.name) }
		}

	}
}
