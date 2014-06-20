package viscel.store

import scala.collection.JavaConverters._

trait Metadata {
	this: ViscelNode =>
	def metadata(): Map[String, String] = Neo.txs {
		self.getPropertyKeys().asScala.collect {
			case k if k.startsWith("metadata_") => k.substring("metadata_".length) -> self[String](k)
		}.toMap
	}
	def metadata(key: String): String = Neo.txs { self[String](s"metadata_$key") }
	def metadataOption(key: String): Option[String] = Neo.txs { self.get[String](s"metadata_$key") }
}

object Metadata {
	def prefix(data: Map[String, String]): Map[String, String] = data.map { case (k, v) => s"metadata_$k" -> v }.toMap
}
