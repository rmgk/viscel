package viscel.store.coin

import org.neo4j.graphdb.Node
import viscel.database.{NodeOps, Ntx}
import viscel.shared.Story
import viscel.store._

import scala.language.implicitConversions

final case class Blob(self: Node) extends AnyVal with StoryCoin with Coin {

	def sha1(implicit neo: Ntx): String = self[String]("sha1")
	def mediatype(implicit ntx: Ntx): String = self[String]("mediatype")

	override def story(implicit neo: Ntx): Story = Story.Blob(sha1, mediatype)
}
