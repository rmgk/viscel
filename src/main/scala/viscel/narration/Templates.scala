package viscel.narration

import viscel.narration.Narrator.Wrapper
import viscel.selektiv.Narration.{Condition, ContextW}
import viscel.shared.Vid
import viscel.store.v4.{DataRow, Vurl}

object Templates {

  val Volatile = "Volatile"

  def archivePage(vid: String,
                  pname: String,
                  start: Vurl,
                  wrapArchive: Wrapper,
                  wrapPage: Wrapper,
                 ): NarratorADT =
    NarratorADT(Vid.from(vid), pname, DataRow.Link(start, List(Volatile)) :: Nil,
                Condition(ContextW.map(_.context.contains(Volatile)), wrapArchive, wrapPage))

  def SimpleForward(vid: String,
                    pname: String,
                    start: Vurl,
                    wrapPage: Wrapper
                   ): NarratorADT =
    NarratorADT(Vid.from(vid), pname, DataRow.Link(start) :: Nil, wrapPage)
}
