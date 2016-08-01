package viscel.narration

import java.net.URL

import org.jsoup.nodes.Document
import org.scalactic.{Every, Or}
import viscel.scribe.narration._
import viscel.selection.Report

object Templates {
	def AP(
		pid: String,
		pname: String,
		start: URL,
		wrapArchive: Document => List[PageContent] Or Every[Report],
		wrapPage: Document => List[PageContent] Or Every[Report]
		): Narrator = new AP(start, wrapArchive, wrapPage) {
		override def id: String = pid
		override def name: String = pname
	}

	abstract class AP(
		start: URL,
		wrapArchive: Document => List[PageContent] Or Every[Report],
		wrapPage: Document => List[PageContent] Or Every[Report]
		) extends Narrator {
		override def archive: List[PageContent] = Link(start, Volatile) :: Nil
		override def wrap(doc: Document, more: Link): List[PageContent] Or Every[Report] = more match {
			case Link(_, Volatile, _) => wrapArchive(doc)
			case Link(_, Normal, _) => wrapPage(doc)
		}
	}

	def SF(
		pid: String,
		pname: String,
		start: URL,
		wrapPage: Document => List[PageContent] Or Every[Report]
		): Narrator = new SF(start, wrapPage) {
		override def id: String = pid
		override def name: String = pname
	}

	abstract class SF(
		start: URL,
		wrapPage: Document => List[PageContent] Or Every[Report]
		) extends Narrator {
		override def archive: List[PageContent] = Link(start) :: Nil
		override def wrap(doc: Document, more: Link): List[PageContent] Or Every[Report] = more match {
			case _ => wrapPage(doc)
		}
	}
}
