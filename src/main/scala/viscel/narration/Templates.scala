package viscel.narration

import org.jsoup.nodes.Document
import org.scalactic.{ErrorMessage, Every, Or}
import viscel.narration.SelectUtil.storyFromOr
import viscel.shared.Story.More
import viscel.shared.{Story, ViscelUrl}

object Templates {
	def AP(
		pid: String, pname: String,
		start: ViscelUrl,
		wrapArchive: Document => List[Story] Or Every[ErrorMessage],
		wrapPage: Document => List[Story] Or Every[ErrorMessage],
		archiveString: String = "archive",
		pageString: String = "page"
	): Narrator = new Narrator {
		override def id: String = pid
		override def name: String = pname
		override def archive: List[Story] = More(start, archiveString) :: Nil
		override def wrap(doc: Document, kind: String): List[Story] = storyFromOr(kind match {
			case `archiveString` => wrapArchive(doc)
			case `pageString` => wrapPage(doc)
		})
	}

	def SF(
		pid: String,
		pname: String,
		start: ViscelUrl,
		wrapPage: Document => List[Story] Or Every[ErrorMessage],
		pageString: String = ""
	): Narrator = new Narrator {
		override def id: String = pid
		override def name: String = pname
		override def archive: List[Story] = More(start, pageString) :: Nil
		override def wrap(doc: Document, kind: String): List[Story] = storyFromOr(kind match {
			case _ => wrapPage(doc)
		})
	}
}
