package viscel.narration

import org.jsoup.nodes.Document
import viscel.narration.interpretation.NarrationInterpretation.{DocumentWrapper, NarratorADT, PolicyDecision, Wrapper}
import viscel.scribe.{Link, Volatile, Vurl}

object Templates {
  def archivePageWrapped(pid: String,
                         pname: String,
                         start: Vurl,
                         wrapArchive: Document => Contents,
                         wrapPage: Document => Contents
                 ): NarratorADT =
    NarratorADT(pid, pname, Link(start, Volatile) :: Nil,
      PolicyDecision(
        volatile = DocumentWrapper(wrapArchive),
        normal = DocumentWrapper(wrapPage)))

  def archivePage(pid: String,
                  pname: String,
                  start: Vurl,
                  wrapArchive: Wrapper,
                  wrapPage: Wrapper,
                 ): NarratorADT =
    NarratorADT(pid, pname, Link(start, Volatile) :: Nil,
      PolicyDecision(
        volatile = wrapArchive,
        normal = wrapPage))

  def SimpleForward(pid: String,
                    pname: String,
                    start: Vurl,
                    wrapPage: Document => Contents
                   ): NarratorADT =
    NarratorADT(pid, pname, Link(start) :: Nil, DocumentWrapper(wrapPage))
}
