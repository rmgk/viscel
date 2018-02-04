package viscel.narration

import org.jsoup.nodes.Document
import viscel.narration.interpretation.NarrationInterpretation
import viscel.narration.interpretation.NarrationInterpretation.{DocumentWrapper, PolicyDecision}
import viscel.scribe.{Link, Volatile, Vurl, WebContent}

object Templates {
  def ArchivePage(pid: String,
                  pname: String,
                  start: Vurl,
                  wrapArchive: Document => Contents,
                  wrapPage: Document => Contents
                 ): Narrator = new ArchivePage(start, wrapArchive, wrapPage) {
    override def id: String = pid
    override def name: String = pname
  }

  abstract class ArchivePage(start: Vurl,
                             wrapArchive: Document => Contents,
                             wrapPage: Document => Contents
                            ) extends Narrator {
    override def archive: List[WebContent] = Link(start, Volatile) :: Nil
    override def wrap(doc: Document, more: Link): Contents = ???
    override def wrapper: NarrationInterpretation.Wrapper = PolicyDecision(
        volatile = DocumentWrapper(wrapArchive),
        normal = DocumentWrapper(wrapPage))
  }

  def SimpleForward(pid: String,
                    pname: String,
                    start: Vurl,
                    wrapPage: Document => Contents
                   ): Narrator = new SimpleForward(start, wrapPage) {
    override def id: String = pid
    override def name: String = pname
  }

  abstract class SimpleForward(start: Vurl,
                               wrapPage: Document => Contents
                              ) extends Narrator {
    override def archive: List[WebContent] = Link(start) :: Nil
    override def wrap(doc: Document, more: Link): Contents = ???
    override def wrapper: NarrationInterpretation.Wrapper = DocumentWrapper(wrapPage)
  }
}
