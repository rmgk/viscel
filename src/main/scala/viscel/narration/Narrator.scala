package viscel.narration

import org.jsoup.nodes.Document
import org.scalactic.TypeCheckedTripleEquals._
import viscel.narration.narrators._
import viscel.shared.Story

import scala.Predef.ArrowAssoc
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
	def all: Set[Narrator] = narrators
	def get(id: String): Option[Narrator] = narratorMap.get(id)

	private val narrators = Set() ++
		KatBox.cores ++
		PetiteSymphony.cores ++
		WordpressEasel.cores ++
		Batoto.cores ++
		Sequential.cores ++
		Funish.cores ++
		CloneManga.MetaClone.load() ++
		MangaHere.MetaCore.load() ++
		Set(Flipside, Everafter, CitrusSaburoUta, Misfile,
			Twokinds, JayNaylor.BetterDays, JayNaylor.OriginalLife, MenageA3,
			Building12)

	private val narratorMap = narrators.map(n => n.id -> n).toMap
}
