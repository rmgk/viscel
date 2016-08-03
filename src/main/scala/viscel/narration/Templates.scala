package viscel.narration

import org.jsoup.nodes.Document
import org.scalactic.{Every, Or}
import viscel.scribe.{Link, Normal, Volatile, Vurl, WebContent}
import viscel.selection.Report

object Templates {
	def AP(
		pid: String,
		pname: String,
		start: Vurl,
		wrapArchive: Document => List[WebContent] Or Every[Report],
		wrapPage: Document => List[WebContent] Or Every[Report]
		): Narrator = new AP(start, wrapArchive, wrapPage) {
		override def id: String = pid
		override def name: String = pname
	}

	abstract class AP(
		start: Vurl,
		wrapArchive: Document => List[WebContent] Or Every[Report],
		wrapPage: Document => List[WebContent] Or Every[Report]
		) extends Narrator {
		override def archive: List[WebContent] = Link(start, Volatile) :: Nil
		override def wrap(doc: Document, more: Link): List[WebContent] Or Every[Report] = more match {
			case Link(_, Volatile, _) => wrapArchive(doc)
			case Link(_, Normal, _) => wrapPage(doc)
		}
	}

	def SF(
		pid: String,
		pname: String,
		start: Vurl,
		wrapPage: Document => List[WebContent] Or Every[Report]
		): Narrator = new SF(start, wrapPage) {
		override def id: String = pid
		override def name: String = pname
	}

	abstract class SF(
		start: Vurl,
		wrapPage: Document => List[WebContent] Or Every[Report]
		) extends Narrator {
		override def archive: List[WebContent] = Link(start) :: Nil
		override def wrap(doc: Document, more: Link): List[WebContent] Or Every[Report] = more match {
			case _ => wrapPage(doc)
		}
	}
}
