package viscel.store.coin

import org.neo4j.graphdb.Node
import spray.http.{MediaType, MediaTypes}
import viscel.store._
import viscel.database.{Ntx, NodeOps}

import scala.Predef.any2ArrowAssoc
import scala.language.implicitConversions

final case class Blob(self: Node) extends Coin {

	def sha1(implicit neo: Ntx): String = self[String]("sha1")

	def mediatype(implicit neo: Ntx): MediaType = {
		val mstring: String = self[String]("mediatype")
		val split = mstring.split("/")
		MediaTypes.getForKey(split(0) -> split(1)).get
	}

	def source(implicit neo: Ntx) = self[String]("source")

	override def toString = s"Blob($self)"
}
