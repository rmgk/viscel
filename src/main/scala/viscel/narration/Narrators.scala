package viscel.narration

import viscel.narration.narrators.Individual._
import viscel.narration.narrators._

import scala.Predef.{$conforms, ArrowAssoc}
import scala.collection.immutable.{Map, Set}


object Narrators {

	private val staticV2: Set[Narrator] =
		Individual.inlineCores ++
			KatBox.cores ++
			PetiteSymphony.cores ++
			Snafu.cores ++
			CloneManga.cores ++
			Set(Candi, Flipside, Inverloch, JayNaylor.BetterDays, JayNaylor.OriginalLife,
				MenageA3, Misfile, NamirDeiter, YouSayItFirst,
				UnlikeMinerva)

	def calculateAll(): Set[Narrator] = staticV2 ++ Metarrators.cores() ++ Vid.load()

	def update(): Unit = {
		cached = calculateAll()
		narratorMap = all.map(n => n.id -> n).toMap
	}

	@volatile private var cached: Set[Narrator] = calculateAll()
	def all: Set[Narrator] = synchronized(cached)

	@volatile private var narratorMap: Map[String, Narrator] = all.map(n => n.id -> n).toMap
	def get(id: String): Option[Narrator] = narratorMap.get(id)

}
