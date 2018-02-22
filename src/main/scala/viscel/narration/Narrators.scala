package viscel.narration

import viscel.narration.narrators.Individual._
import viscel.narration.narrators._

object Narrators {

  val staticV2: Set[Narrator] =
    Individual.inlineCores ++
      KatBox.cores ++
      PetiteSymphony.cores ++
      Snafu.cores ++
      CloneManga.cores ++
      Set(Candi, Flipside, Inverloch, JayNaylor.BetterDays, JayNaylor.OriginalLife,
        Misfile, NamirDeiter, YouSayItFirst,
        UnlikeMinerva, DynastyScans.Citrus)

  val metas: List[Metarrator[_]] = MangaHere :: Comicfury :: Mangadex :: Nil

}
