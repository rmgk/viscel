package viscel.narration

import viscel.crawl.Decider
import viscel.narration.Narrator.Wrapper
import viscel.selektiv.Narration.{Condition, ContextW}
import viscel.shared.Vid
import viscel.store.v4.{DataRow, Vurl}

object Templates {
  def archivePage(vid: String,
                  pname: String,
                  start: Vurl,
                  wrapArchive: Wrapper,
                  wrapPage: Wrapper,
                 ): Narrator =
    Narrator(Vid.from(vid), pname, DataRow.Link(start, List(Decider.Volatile)) :: Nil,
                Condition(ContextW.map(_.request.context.contains(Decider.Volatile)), wrapArchive, wrapPage))

  def SimpleForward(vid: String,
                    pname: String,
                    start: Vurl,
                    wrapPage: Wrapper
                   ): Narrator =
    Narrator(Vid.from(vid), pname, DataRow.Link(start) :: Nil, wrapPage)
}
