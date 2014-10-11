package viscel.narration

import org.jsoup.nodes.Document
import org.scalactic.TypeCheckedTripleEquals._
import viscel.narration.narrators._
import viscel.database.{NeoSingleton, Ntx, label}
import viscel.store.coin

import scala.collection.immutable.Set

trait Narrator {
	def id: String
	def name: String
	def archive: List[Story]
	def wrap(doc: Document, pd: Story.More): List[Story]
	override def equals(other: Any) = other match {
		case o: Narrator => id === o.id
		case _ => false
	}
	override def hashCode: Int = id.hashCode
	override def toString: String = s"$id($name)"
}

object Narrator {
	def metaCores(implicit ntx: Ntx): Set[Narrator] =
		ntx.nodes(label.Core).map(coin.Core.apply).map { core =>
			core.kind match {
				case "CloneManga" => CloneManga.getCore(core.story)
				case "MangaHere" => MangaHere.getCore(core.story)
			}
		}.toSet

	def availableCores: Set[Narrator] = KatBox.cores ++ PetiteSymphony.cores ++ WordpressEasel.cores ++ Batoto.cores ++ staticCores
	def get(id: String): Option[Narrator] = viscel.time(s"get core $id") { availableCores.find(_.id === id) }

	val staticCores = Set(MangaHere.MetaCore, CloneManga.MetaClone, Flipside, Everafter, CitrusSaburoUta, Misfile, Twokinds, JayNaylor.BetterDays, JayNaylor.OriginalLife)
}
