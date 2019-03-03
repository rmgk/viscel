package viscel.narration

import viscel.narration.narrators.Individual._
import viscel.narration.narrators._

object Narrators {

  val staticV2: Set[Narrator] =
    Individual.inlineCores ++
      PetiteSymphony.cores ++
      Snafu.cores ++
      CloneManga.cores ++
      Set(Inverloch, Misfile, UnlikeMinerva)

  val metas: List[Metarrator[_]] = Comicfury :: Mangadex :: Nil

}
