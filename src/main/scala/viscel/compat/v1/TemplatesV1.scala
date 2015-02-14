package viscel.compat.v1

import org.jsoup.nodes.Document
import org.scalactic.{ErrorMessage, Every, Or}
import viscel.compat.v1.SelectUtilV1.storyFromOr
import viscel.compat.v1.Story.More
import viscel.compat.v1.Story.More.{Archive, Kind, Page, Unused}

object TemplatesV1 {
	def AP(
		pid: String, pname: String,
		start: ViscelUrl,
		wrapArchive: Document => List[Story] Or Every[ErrorMessage],
		wrapPage: Document => List[Story] Or Every[ErrorMessage]
	): NarratorV1 = new NarratorV1 {
		override def id: String = pid
		override def name: String = pname
		override def archive: List[Story] = More(start, Archive) :: Nil
		override def wrap(doc: Document, kind: Kind): List[Story] = storyFromOr(kind match {
			case Archive => wrapArchive(doc)
			case Page => wrapPage(doc)
		})
	}

	def SF(
		pid: String,
		pname: String,
		start: ViscelUrl,
		wrapPage: Document => List[Story] Or Every[ErrorMessage]
	): NarratorV1 = new NarratorV1 {
		override def id: String = pid
		override def name: String = pname
		override def archive: List[Story] = More(start, Unused) :: Nil
		override def wrap(doc: Document, kind: Kind): List[Story] = storyFromOr(kind match {
			case _ => wrapPage(doc)
		})
	}
}
