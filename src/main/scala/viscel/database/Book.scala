package viscel.database

import org.neo4j.graphdb.Node
import viscel.database.Implicits.NodeOps
import viscel.shared.Story.{Asset, Chapter, Description}
import viscel.shared.{Story}

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

	private def calcSize()(implicit ntx: Ntx): Int = self.fold(0)(s => {
		case n if n.hasLabel(label.Asset) => s + 1
		case _ => s
	})

	def content()(implicit ntx: Ntx): (List[Asset], List[(Int, Chapter)]) = {
		def allAssets(node: Node): (Int, List[Story.Asset], List[(Int, Story.Chapter)]) = {
			node.fold((0, List[Story.Asset](), List[(Int, Story.Chapter)]())) {
				case state@(pos, assets, chapters) => NeoCodec.load[Story](_) match {
					case asset@Asset(_, _, _, _) => (pos + 1, asset :: assets, if (chapters.isEmpty) List((0, Chapter(""))) else chapters)
					case chapter@Chapter(_, _) => (pos, assets, (pos, chapter) :: chapters)
					case _ => state
				}
			}
		}
		val (size, assets, chapters) = allAssets(self)
		(assets.reverse, chapters)
	}

	def description()(implicit ntx: Ntx): Description = Description(id, name, size)

}
