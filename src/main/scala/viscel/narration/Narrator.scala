package viscel.narration

import org.jsoup.nodes.Document
import org.scalactic.TypeCheckedTripleEquals._
import viscel.database.{NeoCodec, Ntx, label}
import viscel.narration.narrators._
import viscel.shared.Story
import viscel.shared.Story.{Core, More}

import scala.collection.immutable.Set

trait Narrator {
	def id: String
	def name: String
	def archive: List[Story]
	def wrap(doc: Document, kind: String): List[Story]
	final override def equals(other: Any) = other match {
		case o: Narrator => id === o.id
		case _ => false
	}
	final override def hashCode: Int = id.hashCode
	override def toString: String = s"$id($name)"
}

object Narrator {
	def metaCores(implicit ntx: Ntx): Set[Narrator] =
		ntx.nodes(label.Core).map {
			NeoCodec.story[Story](_) match {
				case core@Core("CloneManga", _, _, _) => CloneManga.getCore(core)
				case core@Core("MangaHere", _, _, _) => CloneManga.getCore(core)
				case node @ _ => throw new IllegalStateException(s"$node is not a valid core")
			}
		}.toSet

	def availableCores: Set[Narrator] = KatBox.cores ++ PetiteSymphony.cores ++ WordpressEasel.cores ++ Batoto.cores ++ staticCores
	def get(id: String): Option[Narrator] = availableCores.find(_.id === id)

	val staticCores = Set(MangaHere.MetaCore, CloneManga.MetaClone, Flipside, Everafter, CitrusSaburoUta, Misfile, Twokinds, JayNaylor.BetterDays, JayNaylor.OriginalLife)
}
