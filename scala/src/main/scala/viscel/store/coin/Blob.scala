package viscel.store.coin

import org.neo4j.graphdb.Node
import spray.http.{MediaType, MediaTypes}
import viscel.store._
import viscel.store.archive.NodeOps

import scala.Predef.any2ArrowAssoc
import scala.language.implicitConversions

final case class Blob(self: Node) extends Coin {

	def sha1 = self[String]("sha1")

	def mediatype: MediaType = {
		val mstring: String = self[String]("mediatype")
		val split = mstring.split("/")
		MediaTypes.getForKey(split(0) -> split(1)).get
	}

	def source = self[String]("source")

	override def toString = s"Blob($sha1)"
}
