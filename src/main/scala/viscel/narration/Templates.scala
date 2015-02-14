package viscel.narration

import java.net.URL

import org.jsoup.nodes.Document
import org.scalactic.{Every, Or}
import viscel.scribe.narration.{Narrator, Normal, Volatile, More, Story}
import viscel.scribe.report.Report

object Templates {
	def AP(
		pid: String, pname: String,
		start: URL,
		wrapArchive: Document => List[Story] Or Every[Report],
		wrapPage: Document => List[Story] Or Every[Report]
	): Narrator = new Narrator {
		override def id: String = pid
		override def name: String = pname
		override def archive: List[Story] = More(start, Volatile) :: Nil
		override def wrap(doc: Document, more: More): List[Story] Or Every[Report] = more match {
			case More(_, Volatile, _) => wrapArchive(doc)
			case More(_, Normal, _) => wrapPage(doc)
		}
	}

	def SF(
		pid: String,
		pname: String,
		start: URL,
		wrapPage: Document => List[Story] Or Every[Report]
	): Narrator = new Narrator {
		override def id: String = pid
		override def name: String = pname
		override def archive: List[Story] = More(start) :: Nil
		override def wrap(doc: Document, more: More): List[Story] Or Every[Report] = more match {
			case _ => wrapPage(doc)
		}
	}
}
