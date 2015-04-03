package viscel.narration

import viscel.narration.narrators.Individual._
import viscel.narration.narrators._

import scala.Predef.{$conforms, ArrowAssoc}
import scala.collection.immutable.Set


object Narrators {

	private val staticV2 =
		Individual.inlineCores ++
			KatBox.cores ++
			PetiteSymphony.cores ++
			Snafu.cores ++
			CloneManga.cores ++
			Set(Building12, Candi, Flipside, Inverloch, JayNaylor.BetterDays, JayNaylor.OriginalLife,
				KeyShanShan.Key, KeyShanShan.ShanShan, MenageA3, Misfile, NamirDeiter, Twokinds, YouSayItFirst,
				UnlikeMinerva)

	def calculateAll() = staticV2 ++ Metarrators.cores() ++ Vid.load()

	def update() = {
		cached = calculateAll()
		narratorMap = all.map(n => n.id -> n).toMap
	}

	@volatile private var cached: Set[Narrator] = calculateAll()
	def all: Set[Narrator] = synchronized(cached)

	@volatile private var narratorMap = all.map(n => n.id -> n).toMap
	def get(id: String): Option[Narrator] = narratorMap.get(id)

}
