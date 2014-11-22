package viscel.store.coin

import org.neo4j.graphdb.Node
import viscel.database.{NodeOps, Ntx}
import viscel.store.Coin


final case class Config(self: Node) extends AnyVal {

	def version(implicit neo: Ntx) = self[Int]("version")

	def download(size: Long, success: Boolean = true, compressed: Boolean = false)(implicit neo: Ntx): Unit = {
		self.setProperty("stat_download_size", downloaded + size)
		self.setProperty("stat_download_count", downloads + 1)
		self.setProperty("stat_download_count_compressed", downloadsCompressed + 1)
		if (!success) self.setProperty("stat_download_failed", downloadsFailed + 1)
	}

	def downloaded(implicit neo: Ntx): Long = self.get[Long]("stat_download_size").getOrElse(0L)
	def downloads(implicit neo: Ntx): Long = self.get[Long]("stat_download_count").getOrElse(0L)
	def downloadsFailed(implicit neo: Ntx): Long = self.get[Long]("stat_download_failed").getOrElse(0L)
	def downloadsCompressed(implicit neo: Ntx): Long = self.get[Long]("stat_download_count_compressed").getOrElse(0L)

}
