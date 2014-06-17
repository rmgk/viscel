package viscel.wrapper

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import org.scalactic._
import viscel.core._
import viscel.description._

import scala.collection.JavaConversions._
import scala.language.implicitConversions

object Misfile extends Core with WrapperTools with StrictLogging {
	def archive = Pointer("http://www.misfile.com/archives.php?arc=1&displaymode=wide", "archive")

	def id: String = "NX_Misfile"

	def name: String = "Misfile"

	def chapterFrom(name: String, first: AbsUri) = Structure(
		payload = Chapter(name),
		children = Pointer(first, "page") :: Nil)

	def wrapArchive(doc: Document, pd: Pointer): Structure Or Every[ErrorMessage] = {
		val chapters = selectSome(doc, "#comicbody a:matchesOwn(^Book #\\d+$)").map {
			_.map { anchor =>
				chapterFrom(name = anchor.ownText, first = anchor.attr("abs:href"))
			}
		}
		withGood(wrapPage(doc, pd), chapters) { (page, chapters) =>
			Structure(children =
				page.copy(payload = Chapter("Book #1"))
					+: chapters)
		}
	}

	def wrapPage(doc: Document, pd: Pointer): Structure Or Every[ErrorMessage] = {
		selectUnique(doc, ".comiclist table.wide_gallery").flatMap { clist =>
			val elements_? = selectSome(clist, "[id~=^comic_\\d+$] .picture a").flatMap { anchors =>
				anchors.validatedBy { anchor =>
					selectUnique(anchor, "img").map { img =>
						Structure(ElementContent(origin = anchor.attr("abs:href"), source = img.attr("abs:src").replace("/t", "/")))
					}
				}
			}
			val next = doc.select("a.next") match {
				case ns if ns.size > 0 => Pointer(ns(0).attr("abs:href"), "page")
				case ns => EmptyDescription
			}
			elements_?.map { elements => Structure(children = elements, next = next) }
		}
	}

	def wrap(doc: Document, pd: Pointer): Description = Description.fromOr(pd.pagetype match {
		case "archive" => wrapArchive(doc, pd)
		case "page" => wrapPage(doc, pd)
	})
}
