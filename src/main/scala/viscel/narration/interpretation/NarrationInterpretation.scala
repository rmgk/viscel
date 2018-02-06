package viscel.narration.interpretation

import org.jsoup.nodes.Document
import org.scalactic.Every
import viscel.narration.Queries.reverse
import viscel.narration.{Contents, Narrator}
import viscel.scribe.{ImageRef, Link, Normal, Volatile, Vurl, WebContent}
import viscel.selection.Report

object NarrationInterpretation {


  def transformUrls(replacements: List[(String, String)])(stories: List[WebContent]) = {

    def replaceVurl(url: Vurl): Vurl =
      replacements.foldLeft(url.uriString) {
        case (u, (matches, replace)) => u.replaceAll(matches, replace)
      }

    stories.map {
      case Link(url, policy, data) => Link(replaceVurl(url), policy, data)
      case ImageRef(url, origin, data) => ImageRef(replaceVurl(url), origin, data)
      case o => o
    }
  }

  def interpret(outerWrapper: Wrapper, doc: Document, link: Link): Contents = {
    def recurse(wrapper: Wrapper): Contents =
      wrapper match {
        case OmnipotentWrapper(narrator) => narrator(doc, link)
        case DocumentWrapper(wrap) => wrap(doc)
        case PolicyDecision(volatile, normal) => link match {
          case Link(_, Volatile, _) => recurse(volatile)
          case Link(_, Normal, _) => recurse(normal)
        }
        case TransformUrls(target, replacements) => recurse(target).map(transformUrls(replacements))
        case Reverse(target) => recurse(target).map(reverse)
        case AdditionalErrors(target, augmentation) => recurse(target).badMap(augmentation)
      }

    recurse(outerWrapper)
  }

  case class NarratorADT(id: String, name: String, archive: List[WebContent], wrap: Wrapper) extends Narrator {
    override def wrap(doc: Document, link: Link): Contents = interpret(wrap, doc, link)
    override def wrapper: Wrapper = wrap
  }

  sealed trait Wrapper
  case class OmnipotentWrapper(narrator: (Document, Link) => Contents) extends Wrapper
  case class DocumentWrapper(wrapPage: Document => Contents) extends Wrapper
  case class PolicyDecision(volatile: Wrapper, normal: Wrapper) extends Wrapper
  case class TransformUrls(target: Wrapper, replacements: List[(String, String)]) extends Wrapper
  case class Reverse(target: Wrapper) extends Wrapper
  case class AdditionalErrors(target: Wrapper, augmentation: Every[Report] => Every[Report]) extends Wrapper

}

