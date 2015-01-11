package viscel.narration

import viscel.narration.narrators._

import scala.Predef.ArrowAssoc
import scala.collection.immutable.Set


object Narrators {
	def all: Set[Narrator] = narrators
	def get(id: String): Option[Narrator] = narratorMap.get(id)

	private val narrators = Set() ++
		KatBox.cores ++
		PetiteSymphony.cores ++
		WordpressEasel.cores ++
		Batoto.cores ++
		Funish.cores ++
		CloneManga.MetaClone.load() ++
		MangaHere.MetaCore.load() ++
		Set(Flipside, Everafter, CitrusSaburoUta, Misfile,
			Twokinds, JayNaylor.BetterDays, JayNaylor.OriginalLife, MenageA3,
			Building12)

	private val narratorMap = narrators.map(n => n.id -> n).toMap
}
