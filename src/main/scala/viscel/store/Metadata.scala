package viscel.store

import viscel.database.{Ntx, NodeOps}

import scala.Predef.any2ArrowAssoc
import scala.collection.JavaConverters._
import scala.collection.immutable.Map


trait Metadata extends Any {
	this: Coin =>

	import viscel.store.Metadata.prefix

	def metadata()(implicit neo: Ntx): Map[String, String] =
		self.getPropertyKeys().asScala.collect {
			case k if k.startsWith(prefix) => k.substring(prefix.length) -> self[String](k)
		}.toMap

	def metadataOption(key: String)(implicit ntx: Ntx): Option[String] = self.get[String](prefix + key)
}

object Metadata {
	private val prefix = "metadata_"
	def prefix(data: Map[String, String]): Map[String, String] = data.map { case (k, v) => (prefix + k) -> v }.toMap
}
