package viscel.narration.interpretation

import org.jsoup.nodes.Document
import viscel.narration.Contents
import viscel.scribe.{Link, Normal, Volatile, WebContent}

object NarrationInterpretation {
  def interpret(wrapper: Wrapper, doc: Document, link: Link): Contents = wrapper match {
    case OmnipotentWrapper(narrator) => narrator(doc, link)
    case DocumentWrapper(wrap) => wrap(doc)
    case PolicyDecision(volatile, normal) => link match {
      case Link(_, Volatile, _) => interpret(volatile, doc, link)
      case Link(_, Normal, _) => interpret(normal, doc, link)
    }
  }

  case class NarratorADT(id: String, name: String, archive: List[WebContent], wrap: Wrapper)

  sealed trait Wrapper
  case class OmnipotentWrapper(narrator: (Document , Link) => Contents) extends Wrapper
  case class DocumentWrapper(wrapPage: Document => Contents) extends Wrapper
  case class PolicyDecision(volatile: Wrapper, normal: Wrapper) extends Wrapper


}

