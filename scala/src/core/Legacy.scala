package viscel.core

import com.typesafe.scalalogging.slf4j.Logging
import scala.concurrent.ExecutionContext.Implicits.global
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import org.jsoup.nodes.Element
import scala.concurrent._
import scala.util._
import spray.client.pipelining._
import spray.http.Uri
import viscel._
import scala.collection.JavaConversions._
import scala.language.dynamics
import com.twitter.util.Eval
import scala.util.matching.Regex

case class LegacyCollection(
	id: String,
	name: String,
	start: String,
	criteria: Seq[Seq[Any]],
	next: Option[Seq[Seq[Any]]] = None,
	url_hack: Option[String] = None,
	custom_match: Option[String] = None)

class LegacyCore(val id: String, val name: String, start: String, elementSelector: String, nextSelector: Option[String]) extends Core with Logging with WrapperTools {

	def archive = FullArchive(Seq(LinkedChapter(first = PagePointer(Uri.parseAbsolute(start)), name = "")))
	def wrapArchive(doc: Document) = ???
	def wrapPage(doc: Document) = {
		selectUnique(doc, elementSelector).map { main =>
			val img = main.select("img")
			val elements = img.map { imgToElement }

			val next = nextSelector.pipe {
				case Some(nextSelector) => selectUnique(doc, nextSelector).flatMap { next =>
					findParentTag(next, "a").orElse { selectUnique(next, "a").toOption }.fold { Try[Element](throw EndRun("next not found")) } { Try(_) }
				}
				case None => findParentTag(img(0), "a").pipe {
					case Some(t) => Try(t)
					case None => Try { throw EndRun("image has no anchor parent") }
				}.pipe { res =>
					if (res.isFailure || res.get.attr("href") == "" || res.get.attr("href").matches("""(?i).*\.(jpe?g|gif|png|bmp)(\W|$).*"""))
						Try {
							val candidates = doc.select("a[rel=next], a:contains(next)")
							if (candidates.isEmpty) throw EndRun("no next found")
							else candidates(0)
						}
					else res
				}
			}.map { _.attr("abs:href").pipe { Uri.parseAbsolute(_) }.pipe { PagePointer(_) } }

			FullPage(elements = elements, next = next, loc = doc.baseUri)
		}
	}

}

object LegacyCores extends Logging {

	def criteriaToSelector(crit: Seq[Seq[Any]]) = {
		def singleTag(tdesc: Seq[Any]) = {
			val trgx = "(?i)_tag".r
			val sb = new StringBuilder()
			tdesc.toList.grouped(2).foreach {
				case trgx() :: tag :: Nil => sb.insert(0, tag)
				case attr :: value :: Nil if value.isInstanceOf[String] => sb.append(s"[$attr=$value]")
				case attr :: value :: Nil if value.isInstanceOf[Regex] => sb.append(s"[$attr~=$value]")
			}
			sb
		}
		crit.map { singleTag }.mkString(" ")
	}

	def list = {
		val collections = UclistPort.get
		val cols = collections.filter { lc =>
			val entries = (lc.criteria ++ lc.next.getOrElse(Seq())).flatten :+ lc.url_hack :+ lc.custom_match
			entries.forall { case Some(_) => false; case _ => true }
		}
		cols.map { col =>
			new LegacyCore(id = col.id, name = col.name, start = col.start,
				elementSelector = criteriaToSelector(col.criteria),
				nextSelector = col.next.map { criteriaToSelector })
		}

	}
}
