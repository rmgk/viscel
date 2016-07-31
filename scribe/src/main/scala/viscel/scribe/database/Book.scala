package viscel.scribe.database

import java.net.URL

import org.neo4j.graphdb.Node
import viscel.scribe.database.Implicits.NodeOps
import viscel.scribe.narration.{Asset, Blob, More, Page, Story}

import scala.annotation.tailrec
import scala.util.Try

final case class Book(self: Node) extends AnyVal {

	def id(implicit ntx: Ntx): String = self.prop[String]("id")
	def name(implicit ntx: Ntx): String = self.get[String]("name").getOrElse(id)
	def name_=(value: String)(implicit neo: Ntx) = self.setProperty("name", value)
	def size(kind: Byte)(implicit ntx: Ntx): Int = {
		val sopt = self.get[Array[Int]]("size")
		val res = sopt.getOrElse {
			val size = calcSize()
			self.setProperty("size", size)
			size
		}
		if (kind < res.length) res(kind) else 0
	}

	def invalidateSize()(implicit ntx: Ntx) = self.removeProperty("size")

	private def calcSize()(implicit ntx: Ntx): Array[Int] = {
		val mapped = self.layer.recursive
			.filter(_.hasLabel(label.Asset))
			.groupBy(_.prop[Byte]("kind"))
			.mapValues(_.length)
		val size = Try(mapped.keys.max).getOrElse[Byte](-1) + 1
		val res = new Array[Int](size)
		mapped.foreach { case (k, v) => res(k) = v }
		res
	}

	def pages()(implicit ntx: Ntx): List[Page] =
		self.layer.recursive.collect {
			case n if n hasLabel label.Asset =>
				val asset = Codec.load[Asset](n)
				val blob = Option(n.to(rel.blob)).map(Codec.load[Blob])
				Page(asset, blob)
		}


}
