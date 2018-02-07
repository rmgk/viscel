package viscel.narration.interpretation

import org.jsoup.nodes.{Document, Element}
import org.scalactic.{Every, Or}
import viscel.narration.Queries.reverse
import viscel.narration.{Contents, Narrator}
import viscel.scribe.{ImageRef, Link, Normal, Volatile, Vurl, WebContent}
import viscel.selection.Report
import org.scalactic.Accumulation.convertGenTraversableOnceToValidatable


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

  def interpret[T](outerWrapper: WrapPart[T], doc: Document, link: Link): T = {
    def recurse(wrapper: WrapPart[T]): T =
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
        case SelectionWrapOne(selection, fun) => fun(selection(doc)).map(List.apply(_))
        case SelectionWrapEach(sel, fun) => sel(doc).validatedBy(fun)
        case SelectionWrapFlat(sel, fun) => sel(doc).validatedBy(fun).map(_.flatten)
      }

    recurse(outerWrapper)
  }

  case class NarratorADT(id: String, name: String, archive: List[WebContent], wrap: WrapPart[Contents]) extends Narrator {
    override def wrap(doc: Document, link: Link): Contents = interpret(wrap, doc, link)
    override def wrapper: Wrapper = wrap
  }

  type Wrapper = WrapPart[Contents]

  sealed trait WrapPart[T]
  case class OmnipotentWrapper(narrator: (Document, Link) => Contents) extends Wrapper
  case class DocumentWrapper(wrapPage: Document => Contents) extends Wrapper
  case class PolicyDecision[T](volatile: WrapPart[T], normal: WrapPart[T]) extends WrapPart[T]
  case class TransformUrls(target: Wrapper, replacements: List[(String, String)]) extends Wrapper
  case class Reverse(target: Wrapper) extends Wrapper
  case class AdditionalErrors[E](target: WrapPart[E Or Every[Report]], augmentation: Every[Report] => Every[Report]) extends WrapPart[E Or Every[Report]]

  case class SelectionWrapOne[R](selection: Document => Element, fun: Element => WebContent Or Every[Report]) extends Wrapper
  case class SelectionWrapEach[R](selection: Document => List[Element], fun: Element => WebContent Or Every[Report]) extends Wrapper
  case class SelectionWrapFlat[R](selection: Document => List[Element], fun: Element => List[WebContent] Or Every[Report]) extends Wrapper

}

