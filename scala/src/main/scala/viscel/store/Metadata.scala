package viscel.store

import viscel.store.archive.{Neo, NodeOps}

import scala.Predef.any2ArrowAssoc
import scala.collection.JavaConverters._
import scala.collection.immutable.Map


trait Metadata {
	this: Coin =>
	import viscel.store.Metadata.prefix

	def metadata(): Map[String, String] = Neo.txs {
		self.getPropertyKeys().asScala.collect {
			case k if k.startsWith(prefix) => k.substring(prefix.length) -> self[String](k)
		}.toMap
	}
	def metadataOption(key: String): Option[String] = Neo.txs { self.get[String](prefix + key) }
}

object Metadata {
	private val prefix = "metadata_"
	def prefix(data: Map[String, String]): Map[String, String] = data.map { case (k, v) => (prefix + k) -> v }.toMap
}
