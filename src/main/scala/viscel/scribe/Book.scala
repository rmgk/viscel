package viscel.scribe

import org.neo4j.graphdb.Node
import viscel.scribe.database.Implicits.NodeOps
import viscel.scribe.database.{Codec, Ntx, label, rel}
import viscel.scribe.narration.{Asset, Blob, Page}

final case class Book(self: Node) extends AnyVal {

	def id(implicit ntx: Ntx): String = self.prop[String]("id")
	def name(implicit ntx: Ntx): String = self.get[String]("name").getOrElse(id)
	def name_=(value: String)(implicit neo: Ntx) = self.setProperty("name", value)
	def size(implicit ntx: Ntx): Int = {
		val sopt = self.get[Int]("size")
		sopt.getOrElse {
			val size = calcSize()
			self.setProperty("size", size)
			size
		}
	}

	private def calcSize()(implicit ntx: Ntx): Int =
		self.fold(0)(s => {
			case n if n.hasLabel(label.Asset) => s + 1
			case _ => s
		})

	def pages()(implicit ntx: Ntx): List[Page] =
		self.fold(List[Page]())(s => n => {
			if (!n.hasLabel(label.Asset)) s.reverse
			else {
				val asset = Codec.load[Asset](n)
				val blob = Option(n.to(rel.blob)).map(Codec.load[Blob])
				Page(asset, blob) :: s
			}
		})

}
