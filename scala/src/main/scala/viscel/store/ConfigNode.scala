package viscel.store

import org.neo4j.graphdb.Node

import scala.Predef.any2ArrowAssoc

class ConfigNode(val self: Node) extends ViscelNode {

	def selfLabel = label.Config

	def version = Neo.txs { self[Int]("version") }

	def download(size: Long, success: Boolean = true, compressed: Boolean = false): Unit = Neo.txs {
		self.setProperty("stat_download_size", downloaded + size)
		self.setProperty("stat_download_count", downloads + 1)
		self.setProperty("stat_download_count_compressed", downloadsCompressed + 1)
		if (!success) self.setProperty("stat_download_failed", downloadsFailed + 1)
	}

	def downloaded: Long = Neo.txs { self.get[Long]("stat_download_size").getOrElse(0L) }
	def downloads: Long = Neo.txs { self.get[Long]("stat_download_count").getOrElse(0L) }
	def downloadsFailed: Long = Neo.txs { self.get[Long]("stat_download_failed").getOrElse(0L) }
	def downloadsCompressed: Long = Neo.txs { self.get[Long]("stat_download_count_compressed").getOrElse(0L) }

	def legacyCollections_=(cols: Seq[String]) = Neo.txs {
		self.setProperty("selected_legacy_collections", cols.toArray)
	}

	def legacyCollections: Seq[String] = Neo.txs {
		self.get[Array[String]]("selected_legacy_collections").toSeq.flatten(Predef.wrapRefArray)
	}

}

object ConfigNode {
	lazy val cached = {
		val node = Neo.txs {
			Neo.node(label.Config, "id", "config").getOrElse {
				Neo.create(label.Config, "id" -> "config", "version" -> 1)
			}
		}
		new ConfigNode(node)
	}

	def apply() = cached
}
