package viscel.narration

import viscel.narration.interpretation.NarrationInterpretation.{NarratorADT, PolicyDecision, Wrapper}
import viscel.shared.Vid
import viscel.store.{Link, Volatile, Vurl}

object Templates {
  def archivePage(vid: String,
                  pname: String,
                  start: Vurl,
                  wrapArchive: Wrapper,
                  wrapPage: Wrapper,
                 ): NarratorADT =
    NarratorADT(Vid.from(vid), pname, Link(start, Volatile) :: Nil,
      PolicyDecision(
        volatile = wrapArchive,
        normal = wrapPage))

  def SimpleForward(vid: String,
                    pname: String,
                    start: Vurl,
                    wrapPage: Wrapper
                   ): NarratorADT =
    NarratorADT(Vid.from(vid), pname, Link(start) :: Nil, wrapPage)
}
