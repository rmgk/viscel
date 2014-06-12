package viscel.newCore

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import scala.language.implicitConversions
import scala.util._
import viscel._
import scala.collection.JavaConversions._

trait WrapperTools extends StrictLogging {

	def fail(msg: String) = Try { throw new Throwable(msg) }

	def getAttr(e: Element, k: String): Option[(String, String)] = {
		val res = e.attr(k)
		if (res.isEmpty) None else Some(k -> res)
	}

	def imgToElement(img: Element): ElementDescription = ElementDescription(
		source = img.attr("abs:src"),
		origin = img.baseUri,
		props = (getAttr(img, "alt") ++
			getAttr(img, "title") ++
			getAttr(img, "width") ++
			getAttr(img, "height")).toMap)

	def selectUnique(from: Element, query: String) = from.select(query) match {
		case rs if rs.size > 2 => fail(s"query not unique ($query) on (${from.baseUri})")
		case rs if rs.size < 1 => fail(s"query not found ($query) on (${from.baseUri})")
		case rs => Try { rs(0) }
	}

	def findParentTag(from: Element, tagname: String): Option[Element] =
		if (from.tag.getName == tagname) Some(from)
		else from.parents.find(_.tag.getName == tagname)

	implicit def tryToDescr[D <: Description](tried: Try[D]): Description = tried match {
		case Success(desc) => desc
		case Failure(t) => FailedDescription(t)
	}

	def chapter(name: String, children: Seq[Description] = Seq(), next: Description = EmptyDescription, props: Map[String, String] = Map()) =
		StructureDescription(
			payload = ChapterDescription(name, props),
			children = children,
			next = next)
}

object Misfile extends Core with WrapperTools with StrictLogging {
	def archive = PointerDescription("http://www.misfile.com/archives.php?arc=1&displaymode=wide", "archive")
	def id: String = "NX_Misfile"
	def name: String = "Misfile"

	def chapterFrom(name: String, first: AbsUri) = StructureDescription(
		payload = ChapterDescription(name),
		children = Seq(PointerDescription(first, "page")))

	def wrap(doc: Document, pd: PointerDescription): Description = pd.pagetype match {
		case "archive" =>
			val chapters = doc.select("#comicbody a:matchesOwn(^Book #\\d+$)").map { anchor =>
				chapterFrom(name = anchor.ownText, first = anchor.attr("abs:href"))
			}
			StructureDescription(children =
				wrap(doc, pd.copy(pagetype = "page"))
					.asInstanceOf[StructureDescription]
					.copy(payload = ChapterDescription("Book #1")) +: chapters)
		case "page" =>
			selectUnique(doc, ".comiclist table.wide_gallery").map { clist =>
				val elements = clist.select("[id~=^comic_\\d+$] .picture a").map { anchor =>
					StructureDescription(ElementDescription(origin = anchor.attr("abs:href"), source = anchor.select("img").attr("abs:src").replace("/t", "/")))
				}
				val next = doc.select("a.next").pipe {
					case ns if ns.size > 0 => PointerDescription(ns(0).attr("abs:href"), "page")
					case ns => EmptyDescription
				}
				StructureDescription(children = elements, next = (next))
			}
	}
}
