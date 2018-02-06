package viscel.narration

import org.jsoup.nodes.Document
import viscel.narration.interpretation.NarrationInterpretation.{DocumentWrapper, NarratorADT, PolicyDecision}
import viscel.scribe.{Link, Volatile, Vurl}

object Templates {
  def ArchivePage(pid: String,
                  pname: String,
                  start: Vurl,
                  wrapArchive: Document => Contents,
                  wrapPage: Document => Contents
                 ): NarratorADT =
    NarratorADT(pid, pname, Link(start, Volatile) :: Nil,
      PolicyDecision(
        volatile = DocumentWrapper(wrapArchive),
        normal = DocumentWrapper(wrapPage)))

  def SimpleForward(pid: String,
                    pname: String,
                    start: Vurl,
                    wrapPage: Document => Contents
                   ): NarratorADT =
    NarratorADT(pid, pname, Link(start) :: Nil, DocumentWrapper(wrapPage))
}
