package viscel.store.coin

import org.neo4j.graphdb.Node
import spray.http.{MediaType, MediaTypes}
import viscel.shared.Story
import viscel.store._
import viscel.database.{Ntx, NodeOps}

import scala.Predef.any2ArrowAssoc
import scala.language.implicitConversions

final case class Blob(self: Node) extends AnyVal with StoryCoin with Coin {

	def sha1(implicit neo: Ntx): String = self[String]("sha1")

	def mediastring(implicit ntx: Ntx): String = self[String]("mediatype")

	def mediatype(implicit neo: Ntx): MediaType = {
		val split = mediastring.split("/")
		MediaTypes.getForKey(split(0) -> split(1)).get
	}

	def source(implicit neo: Ntx) = self[String]("source")

	override def story(implicit neo: Ntx): Story = Story.Blob(sha1, mediastring)
}
