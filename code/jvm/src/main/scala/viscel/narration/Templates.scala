package viscel.narration

import viscel.crawl.Decider
import viscel.narration.Narrator.Wrapper
import viscel.selektiv.Narration.{Condition, ContextW, WrapPart}
import viscel.shared.{DataRow, Vid, Vurl}

object Templates {
  def archivePage(vid: String, pname: String, start: Vurl, wrapArchive: Wrapper, wrapPage: Wrapper): Narrator =
    new Narrator(Vid.from(vid), pname, DataRow.Link(start, List(Decider.Volatile)) :: Nil) {
      override val wrapper: WrapPart[List[DataRow.Content]] =
        Condition(ContextW.map(_.request.context.contains(Decider.Volatile)), wrapArchive, wrapPage)
    }

  def SimpleForward(vid: String, pname: String, start: Vurl, wrapPage: Wrapper): Narrator =
    new Narrator(Vid.from(vid), pname, DataRow.Link(start) :: Nil) {
      override val wrapper: WrapPart[List[DataRow.Content]] = wrapPage
    }
}
